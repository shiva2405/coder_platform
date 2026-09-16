package com.coderplatform.service;

import com.coderplatform.config.ExecutionConfig;
import com.coderplatform.exception.AdmissionRejectedException;
import com.coderplatform.exception.JobNotFoundException;
import com.coderplatform.exception.RateLimitExceededException;
import com.coderplatform.model.WorkState;
import com.coderplatform.model.WorkTicketResponse;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Runs admitted work on a bounded worker pool so HTTP request threads are not
 * held while a job waits in the queue or executes.
 */
@Service
public class QueuedWorkService {

    private static final Logger logger = LoggerFactory.getLogger(QueuedWorkService.class);

    private final ExecutionAdmissionService admission;
    private final ExecutionConfig config;
    private final ConcurrentHashMap<String, Job<?>> jobs = new ConcurrentHashMap<>();
    private final ExecutorService workers;
    private final ScheduledExecutorService janitor;

    public QueuedWorkService(ExecutionAdmissionService admission, ExecutionConfig config) {
        this.admission = admission;
        this.config = config;
        this.workers = Executors.newFixedThreadPool(Math.max(1, config.getMaxConcurrent()), runnable -> {
            Thread thread = new Thread(runnable, "execution-worker");
            thread.setDaemon(true);
            return thread;
        });
        this.janitor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "execution-job-janitor");
            thread.setDaemon(true);
            return thread;
        });
        this.janitor.scheduleAtFixedRate(this::evictExpired, 30, 30, TimeUnit.SECONDS);
    }

    public <T> WorkTicketResponse submit(String language, ClientKey client, Callable<T> work) {
        ExecutionAdmissionService.Handle handle = admission.submit(language, client);
        if (handle.isRejected()) {
            throw handle.rejection() != null
                    ? handle.rejection()
                    : AdmissionRejectedException.queueFull(admission.retryHintSeconds());
        }
        Job<T> job = new Job<>(handle);
        jobs.put(handle.id(), job);
        handle.addListener(new ExecutionAdmissionService.Listener() {
            @Override
            public void onUpdate(int position, long estimatedWaitMs) {
                job.updateQueue(position, estimatedWaitMs);
            }

            @Override
            public void onRejected(String message, long retryAfterSeconds, String reason) {
                job.reject(message, retryAfterSeconds, reason);
            }
        });
        java.util.Map<String, String> callerContext = MDC.getCopyOfContextMap();
        handle.whenReady().whenComplete((lease, error) -> {
            if (error != null) {
                job.fail(error);
                return;
            }
            workers.execute(() -> {
                if (callerContext != null) {
                    MDC.setContextMap(callerContext);
                }
                MDC.put("jobId", handle.id());
                MDC.put("language", language == null ? "" : language);
                job.markRunning();
                try {
                    T result = work.call();
                    job.complete(result);
                } catch (Exception e) {
                    logger.warn("Queued work {} failed", handle.id(), e);
                    job.fail(e);
                } finally {
                    lease.close();
                    MDC.clear();
                }
            });
        });
        return job.toResponse();
    }

    public int jobCount() {
        return jobs.size();
    }

    public WorkTicketResponse get(String id) {
        Job<?> job = jobs.get(id);
        if (job == null) {
            throw new JobNotFoundException(id);
        }
        return job.toResponse();
    }

    public WorkTicketResponse cancel(String id) {
        Job<?> job = jobs.get(id);
        if (job == null) {
            throw new JobNotFoundException(id);
        }
        job.handle.cancel();
        job.cancel();
        return job.toResponse();
    }

    @PreDestroy
    public void shutdown() {
        janitor.shutdownNow();
        workers.shutdownNow();
        try {
            workers.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void evictExpired() {
        long ttl = Math.max(5_000, config.getTicketTtlMs());
        Instant cutoff = Instant.now().minusMillis(ttl);
        jobs.entrySet().removeIf(entry -> entry.getValue().isExpired(cutoff));
    }

    private static final class Job<T> {
        private final ExecutionAdmissionService.Handle handle;
        private volatile WorkState state = WorkState.QUEUED;
        private volatile int position;
        private volatile long estimatedWaitMs;
        private volatile T result;
        private volatile String error;
        private volatile String reason;
        private volatile Long retryAfterSeconds;
        private volatile Instant finishedAt;

        private Job(ExecutionAdmissionService.Handle handle) {
            this.handle = handle;
            this.position = handle.position();
            this.estimatedWaitMs = handle.estimatedWaitMs();
            if (handle.state() == ExecutionAdmissionService.State.RUNNING) {
                this.state = WorkState.RUNNING;
            }
        }

        private synchronized void updateQueue(int nextPosition, long waitMs) {
            if (state != WorkState.QUEUED) {
                return;
            }
            this.position = nextPosition;
            this.estimatedWaitMs = waitMs;
        }

        private synchronized void markRunning() {
            if (state == WorkState.CANCELLED || state == WorkState.REJECTED || state == WorkState.QUEUE_TIMEOUT) {
                return;
            }
            this.state = WorkState.RUNNING;
            this.position = 0;
            this.estimatedWaitMs = 0;
        }

        private synchronized void complete(T value) {
            if (isTerminal()) {
                return;
            }
            this.result = value;
            this.state = WorkState.COMPLETED;
            this.finishedAt = Instant.now();
        }

        private synchronized void reject(String message, long retryAfter, String rejectReason) {
            if (isTerminal()) {
                return;
            }
            this.error = message;
            this.retryAfterSeconds = retryAfter;
            this.reason = rejectReason;
            this.state = AdmissionRejectedException.QUEUE_TIMEOUT.equals(rejectReason)
                    ? WorkState.QUEUE_TIMEOUT
                    : WorkState.REJECTED;
            this.finishedAt = Instant.now();
        }

        private synchronized void fail(Throwable error) {
            if (isTerminal()) {
                return;
            }
            if (error instanceof ExecutionAdmissionService.Cancellation) {
                this.state = WorkState.CANCELLED;
                this.error = "Execution was cancelled";
            } else if (error instanceof AdmissionRejectedException rejected) {
                this.state = AdmissionRejectedException.QUEUE_TIMEOUT.equals(rejected.getReason())
                        ? WorkState.QUEUE_TIMEOUT
                        : WorkState.REJECTED;
                this.error = rejected.getMessage();
                this.reason = rejected.getReason();
                this.retryAfterSeconds = rejected.getRetryAfterSeconds();
            } else if (error instanceof RateLimitExceededException limited) {
                this.state = WorkState.REJECTED;
                this.error = limited.getMessage();
                this.reason = limited.getReason();
                this.retryAfterSeconds = limited.getRetryAfterSeconds();
            } else {
                this.state = WorkState.COMPLETED;
                this.error = error.getMessage() == null ? "Execution failed" : error.getMessage();
            }
            this.finishedAt = Instant.now();
        }

        private synchronized void cancel() {
            if (state == WorkState.QUEUED) {
                this.state = WorkState.CANCELLED;
                this.error = "Execution was cancelled";
                this.finishedAt = Instant.now();
            }
        }

        private boolean isTerminal() {
            return state == WorkState.COMPLETED
                    || state == WorkState.CANCELLED
                    || state == WorkState.REJECTED
                    || state == WorkState.QUEUE_TIMEOUT;
        }

        private boolean isExpired(Instant cutoff) {
            Instant finished = finishedAt;
            return finished != null && finished.isBefore(cutoff);
        }

        private synchronized WorkTicketResponse toResponse() {
            WorkTicketResponse response = new WorkTicketResponse();
            response.setId(handle.id());
            response.setState(state);
            if (state == WorkState.QUEUED) {
                response.setPosition(Math.max(1, position));
                response.setEstimatedWaitMs(Math.max(0, estimatedWaitMs));
            }
            response.setRetryAfterSeconds(retryAfterSeconds);
            response.setReason(reason);
            response.setError(error);
            response.setResult(result);
            return response;
        }
    }
}
