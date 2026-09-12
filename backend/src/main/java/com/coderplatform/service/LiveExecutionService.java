package com.coderplatform.service;

import com.coderplatform.config.ExecutionConfig;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class LiveExecutionService {

    private static final Logger logger = LoggerFactory.getLogger(LiveExecutionService.class);

    private final CodeExecutionService executionService;
    private final ExecutionConfig config;
    private final ConcurrentHashMap<String, LiveExecution> active = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> ownerToExecution = new ConcurrentHashMap<>();
    private final Semaphore slots;
    private final ExecutorService workers;
    private final ScheduledExecutorService scheduler;

    public LiveExecutionService(CodeExecutionService executionService, ExecutionConfig config) {
        this.executionService = executionService;
        this.config = config;
        this.slots = new Semaphore(Math.max(1, config.getMaxConcurrentLive()));
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

    public String start(String ownerId, String language, String code, String initialStdin, LiveExecutionListener listener) {
        if (ownerId == null || ownerId.isBlank()) {
            throw new IllegalArgumentException("Owner is required");
        }
        if (language == null || language.isBlank()) {
            throw new IllegalArgumentException("Language is required");
        }
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Code is required");
        }

        releaseOwner(ownerId);

        if (!slots.tryAcquire()) {
            throw new IllegalStateException("Too many live executions. Try again shortly.");
        }

        String id = UUID.randomUUID().toString();
        AtomicBoolean unregistered = new AtomicBoolean(false);
        LiveExecution execution = new LiveExecution(
                id,
                ownerId,
                config,
                executionService,
                listener,
                () -> unregister(id, ownerId, unregistered)
        );

        active.put(id, execution);
        ownerToExecution.put(ownerId, id);
        listener.onStarted(id);

        try {
            workers.submit(() -> execution.execute(language, code, initialStdin == null ? "" : initialStdin, scheduler));
        } catch (RejectedExecutionException e) {
            execution.stop();
            throw new IllegalStateException("Live execution is unavailable", e);
        }

        logger.info("Started live execution {} for owner {}", id, ownerId);
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
        LiveExecution execution = activeForOwner(ownerId);
        if (execution != null) {
            execution.stop();
        }
    }

    public void releaseOwner(String ownerId) {
        LiveExecution execution = activeForOwner(ownerId);
        if (execution != null) {
            logger.info("Releasing live execution {} after owner {} disconnected", execution.getId(), ownerId);
            execution.abandon();
        }
    }

    public int activeCount() {
        return active.size();
    }

    public boolean hasOwner(String ownerId) {
        return activeForOwner(ownerId) != null;
    }

    @PreDestroy
    public void shutdown() {
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

    private LiveExecution requireActive(String ownerId) {
        LiveExecution execution = activeForOwner(ownerId);
        if (execution == null) {
            throw new IllegalStateException("No running program");
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

    private void unregister(String id, String ownerId, AtomicBoolean unregistered) {
        if (!unregistered.compareAndSet(false, true)) {
            return;
        }
        active.remove(id);
        ownerToExecution.remove(ownerId, id);
        slots.release();
        logger.info("Cleaned up live execution {}", id);
    }
}
