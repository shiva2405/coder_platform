package com.coderplatform.service;

import com.coderplatform.config.ExecutionConfig;
import com.coderplatform.exception.AdmissionRejectedException;
import com.coderplatform.exception.RateLimitExceededException;
import com.coderplatform.model.CodeExecutionResponse;
import com.coderplatform.observability.ExecutionMetrics;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class LiveExecutionService {

    private static final Logger logger = LoggerFactory.getLogger(LiveExecutionService.class);

    private final CodeExecutionService executionService;
    private final ExecutionConfig config;
    private final ExecutionAdmissionService admission;
    private final ExecutionMetrics metrics;
    private final ConcurrentHashMap<String, LiveExecution> active = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> ownerToExecution = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, PendingStart> pending = new ConcurrentHashMap<>();
    private final ExecutorService workers;
    private final ScheduledExecutorService scheduler;

    public LiveExecutionService(
            CodeExecutionService executionService,
            ExecutionConfig config,
            ExecutionAdmissionService admission
    ) {
        this(executionService, config, admission, null);
    }

    @Autowired
    public LiveExecutionService(
            CodeExecutionService executionService,
            ExecutionConfig config,
            ExecutionAdmissionService admission,
            ExecutionMetrics metrics
    ) {
        this.executionService = executionService;
        this.config = config;
        this.admission = admission;
        this.metrics = metrics;
        this.workers = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "live-execution-worker");
            thread.setDaemon(true);
            return thread;
        });
        this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "live-execution-timeout");
            thread.setDaemon(true);
            return thread;
        });
    }

    public String start(
            String ownerId,
            String language,
            String code,
            String initialStdin,
            LiveExecutionListener listener
    ) {
        return start(ownerId, language, code, initialStdin, ClientKey.anonymous(ownerId), listener);
    }

    public String start(
            String ownerId,
            String language,
            String code,
            String initialStdin,
            ClientKey client,
            LiveExecutionListener listener
    ) {
        if (ownerId == null || ownerId.isBlank()) {
            throw new IllegalArgumentException("Owner is required");
        }
        if (language == null || language.isBlank()) {
            throw new IllegalArgumentException("Language is required");
        }
        return start(ownerId, ProjectSources.resolve(language, code, null, null), initialStdin, client, listener);
    }

    public String start(
            String ownerId,
            String language,
            String code,
            java.util.List<com.coderplatform.model.ProjectFile> files,
            String entrypoint,
            String initialStdin,
            ClientKey client,
            LiveExecutionListener listener
    ) {
        return start(ownerId, ProjectSources.resolve(language, code, files, entrypoint), initialStdin, client, listener);
    }

    public String start(
            String ownerId,
            ProjectSources sources,
            String initialStdin,
            ClientKey client,
            LiveExecutionListener listener
    ) {
        if (ownerId == null || ownerId.isBlank()) {
            throw new IllegalArgumentException("Owner is required");
        }
        if (sources == null) {
            throw new IllegalArgumentException("Code is required");
        }
        String language = sources.getLanguage();

        releaseOwner(ownerId);

        ExecutionAdmissionService.Handle handle = admission.submit(language, client);
        if (handle.isRejected()) {
            AdmissionRejectedException rejection = handle.rejection();
            throw rejection != null
                    ? rejection
                    : AdmissionRejectedException.queueFull(admission.retryHintSeconds());
        }

        String id = handle.id();
        PendingStart pendingStart = new PendingStart(id, ownerId, handle, listener);
        pending.put(ownerId, pendingStart);
        ownerToExecution.put(ownerId, id);

        if (handle.state() == ExecutionAdmissionService.State.QUEUED) {
            listener.onQueued(id, Math.max(1, handle.position()), handle.estimatedWaitMs());
            handle.addListener(new ExecutionAdmissionService.Listener() {
                @Override
                public void onUpdate(int position, long estimatedWaitMs) {
                    if (pending.get(ownerId) == pendingStart) {
                        listener.onQueued(id, position, estimatedWaitMs);
                    }
                }

                @Override
                public void onRejected(String message, long retryAfterSeconds, String reason) {
                    if (pending.remove(ownerId, pendingStart)) {
                        ownerToExecution.remove(ownerId, id);
                        listener.onRejected(message, retryAfterSeconds, reason);
                    }
                }
            });
        }

        handle.whenReady().whenComplete((lease, error) -> {
            if (!pending.remove(ownerId, pendingStart)) {
                if (lease != null) {
                    lease.close();
                }
                return;
            }
            if (error != null) {
                ownerToExecution.remove(ownerId, id);
                notifyAdmissionFailure(listener, error);
                return;
            }
            beginExecution(id, ownerId, sources, initialStdin, instrument(language, listener), lease);
        });

        logger.info("Admitted live execution {} for owner {} ({})", id, ownerId, handle.state());
        return id;
    }

    public void writeStdin(String ownerId, String data) throws IOException {
        LiveExecution execution = requireActive(ownerId);
        execution.writeStdin(data == null ? "" : data);
    }

    public void closeStdin(String ownerId) throws IOException {
        LiveExecution execution = requireActive(ownerId);
        execution.closeStdin();
    }

    public void stopOwner(String ownerId) {
        PendingStart queued = pending.remove(ownerId);
        if (queued != null) {
            queued.handle.cancel();
            ownerToExecution.remove(ownerId, queued.id);
            queued.listener.onCompleted(CodeExecutionResponse.stopped("", "", 0));
            return;
        }
        LiveExecution execution = activeForOwner(ownerId);
        if (execution != null) {
            execution.stop();
        }
    }

    public void releaseOwner(String ownerId) {
        PendingStart queued = pending.remove(ownerId);
        if (queued != null) {
            queued.handle.cancel();
            ownerToExecution.remove(ownerId, queued.id);
        }
        LiveExecution execution = activeForOwner(ownerId);
        if (execution != null) {
            logger.info("Releasing live execution {} after owner {} disconnected", execution.getId(), ownerId);
            execution.abandon();
        }
    }

    public int activeCount() {
        return active.size();
    }

    public int pendingCount() {
        return pending.size();
    }

    public boolean hasOwner(String ownerId) {
        return pending.containsKey(ownerId) || activeForOwner(ownerId) != null;
    }

    @PreDestroy
    public void shutdown() {
        for (PendingStart queued : pending.values()) {
            try {
                queued.handle.cancel();
            } catch (Exception e) {
                logger.warn("Failed to cancel queued live execution {}", queued.id, e);
            }
        }
        pending.clear();
        for (LiveExecution execution : active.values()) {
            try {
                execution.stop();
            } catch (Exception e) {
                logger.warn("Failed to stop live execution {} during shutdown", execution.getId(), e);
            }
        }
        workers.shutdownNow();
        scheduler.shutdownNow();
        try {
            workers.awaitTermination(2, TimeUnit.SECONDS);
            scheduler.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void beginExecution(
            String id,
            String ownerId,
            ProjectSources sources,
            String initialStdin,
            LiveExecutionListener listener,
            ExecutionAdmissionService.Lease lease
    ) {
        AtomicBoolean unregistered = new AtomicBoolean(false);
        LiveExecution execution = new LiveExecution(
                id,
                ownerId,
                config,
                executionService,
                listener,
                () -> unregister(id, ownerId, unregistered, lease)
        );

        active.put(id, execution);
        ownerToExecution.put(ownerId, id);
        listener.onStarted(id);

        try {
            workers.submit(() -> execution.execute(sources, initialStdin == null ? "" : initialStdin, scheduler));
        } catch (RejectedExecutionException e) {
            execution.stop();
            throw new IllegalStateException("Live execution is unavailable", e);
        }
    }

    private LiveExecution requireActive(String ownerId) {
        LiveExecution execution = activeForOwner(ownerId);
        if (execution == null) {
            throw new IllegalStateException(pending.containsKey(ownerId)
                    ? "Program is still queued"
                    : "No running program");
        }
        return execution;
    }

    private LiveExecution activeForOwner(String ownerId) {
        String id = ownerToExecution.get(ownerId);
        if (id == null) {
            return null;
        }
        return active.get(id);
    }

    private void unregister(String id, String ownerId, AtomicBoolean unregistered, ExecutionAdmissionService.Lease lease) {
        if (!unregistered.compareAndSet(false, true)) {
            return;
        }
        active.remove(id);
        ownerToExecution.remove(ownerId, id);
        lease.close();
        logger.info("Cleaned up live execution {}", id);
    }

    private LiveExecutionListener instrument(String language, LiveExecutionListener delegate) {
        if (metrics == null || delegate == null) {
            return delegate;
        }
        return new LiveExecutionListener() {
            @Override
            public void onQueued(String executionId, int position, long estimatedWaitMs) {
                delegate.onQueued(executionId, position, estimatedWaitMs);
            }

            @Override
            public void onStarted(String executionId) {
                MDC.put("jobId", executionId);
                MDC.put("language", language);
                delegate.onStarted(executionId);
            }

            @Override
            public void onStdout(String chunk) {
                delegate.onStdout(chunk);
            }

            @Override
            public void onStderr(String chunk) {
                delegate.onStderr(chunk);
            }

            @Override
            public void onCompleted(CodeExecutionResponse result) {
                metrics.record(language, result);
                delegate.onCompleted(result);
            }

            @Override
            public void onRejected(String message, long retryAfterSeconds, String reason) {
                delegate.onRejected(message, retryAfterSeconds, reason);
            }
        };
    }

    private static void notifyAdmissionFailure(LiveExecutionListener listener, Throwable error) {
        if (error instanceof ExecutionAdmissionService.Cancellation) {
            listener.onCompleted(CodeExecutionResponse.stopped("", "", 0));
            return;
        }
        if (error instanceof RateLimitExceededException limited) {
            listener.onRejected(limited.getMessage(), limited.getRetryAfterSeconds(), limited.getReason());
            return;
        }
        listener.onRejected(
                error.getMessage() == null ? "Execution was rejected" : error.getMessage(),
                1,
                AdmissionRejectedException.QUEUE_FULL
        );
    }

    private static final class PendingStart {
        private final String id;
        private final String ownerId;
        private final ExecutionAdmissionService.Handle handle;
        private final LiveExecutionListener listener;

        private PendingStart(
                String id,
                String ownerId,
                ExecutionAdmissionService.Handle handle,
                LiveExecutionListener listener
        ) {
            this.id = id;
            this.ownerId = ownerId;
            this.handle = handle;
            this.listener = listener;
        }
    }
}
