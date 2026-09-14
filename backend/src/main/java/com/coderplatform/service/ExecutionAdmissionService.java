package com.coderplatform.service;

import com.coderplatform.config.ExecutionConfig;
import com.coderplatform.exception.AdmissionRejectedException;
import com.coderplatform.model.Language;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Bounds how many compiler/runtime processes can exist at once.
 * Overflow is parked in a bounded queue; callers are not blocked while waiting.
 * Heavy compiled languages share a smaller slot pool so they cannot starve
 * interpreted languages. Per-client caps stop one user from filling the machine.
 */
@Service
public class ExecutionAdmissionService {

    private static final Logger logger = LoggerFactory.getLogger(ExecutionAdmissionService.class);

    public interface Listener {
        default void onUpdate(int position, long estimatedWaitMs) {
        }

        default void onRejected(String message, long retryAfterSeconds, String reason) {
        }
    }

    public static final class Lease implements AutoCloseable {
        private final Runnable release;
        private boolean closed;

        private Lease(Runnable release) {
            this.release = release;
        }

        @Override
        public synchronized void close() {
            if (closed) {
                return;
            }
            closed = true;
            release.run();
        }
    }

    public static final class Handle {
        private final String id;
        private final boolean heavy;
        private final ClientKey client;
        private final CompletableFuture<Lease> ready = new CompletableFuture<>();
        private final List<Listener> listeners = new ArrayList<>();
        private volatile State state = State.QUEUED;
        private volatile int position;
        private volatile long estimatedWaitMs;
        private volatile String rejectReason;
        private volatile String rejectMessage;
        private volatile long retryAfterSeconds = 1;
        private volatile AdmissionRejectedException rejection;

        private Handle(String id, boolean heavy, ClientKey client) {
            this.id = id;
            this.heavy = heavy;
            this.client = client;
        }

        public String id() {
            return id;
        }

        public State state() {
            return state;
        }

        public int position() {
            return position;
        }

        public long estimatedWaitMs() {
            return estimatedWaitMs;
        }

        public boolean isRejected() {
            return state == State.REJECTED;
        }

        public AdmissionRejectedException rejection() {
            return rejection;
        }

        public CompletableFuture<Lease> whenReady() {
            return ready;
        }

        public synchronized void addListener(Listener listener) {
            if (listener == null) {
                return;
            }
            listeners.add(listener);
            if (state == State.QUEUED) {
                listener.onUpdate(position, estimatedWaitMs);
            } else if (state == State.REJECTED) {
                listener.onRejected(rejectMessage, retryAfterSeconds, rejectReason);
            }
        }

        private Runnable cancelAction = () -> ready.completeExceptionally(new Cancellation());

        public void cancel() {
            cancelAction.run();
        }

        private synchronized List<Listener> snapshotListeners() {
            return List.copyOf(listeners);
        }
    }

    public enum State {
        QUEUED,
        RUNNING,
        REJECTED,
        CANCELLED
    }

    static final class Cancellation extends RuntimeException {
        Cancellation() {
            super("Execution was cancelled");
        }
    }

    private final ExecutionConfig config;
    private final Object lock = new Object();
    private final Deque<Handle> queue = new ArrayDeque<>();
    private final ConcurrentHashMap<String, ClientUsage> clients = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler;
    private final AtomicInteger running = new AtomicInteger();
    private final AtomicInteger heavyRunning = new AtomicInteger();
    private final AtomicLong lightAvgMs = new AtomicLong();
    private final AtomicLong heavyAvgMs = new AtomicLong();

    public ExecutionAdmissionService(ExecutionConfig config) {
        this.config = config;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "execution-admission");
            thread.setDaemon(true);
            return thread;
        });
    }

    public Handle submit(String language, ClientKey client) {
        ClientKey key = client == null ? ClientKey.anonymous("unknown") : client;
        boolean heavy = isHeavy(language);
        Handle handle = new Handle(UUID.randomUUID().toString(), heavy, key);
        handle.cancelAction = () -> cancel(handle);
        List<Ready> started = new ArrayList<>();
        Runnable reject = null;

        synchronized (lock) {
            if (!tryStartLocked(handle, started)) {
                String reason = enqueueRejectReason(handle);
                if (reason != null) {
                    reject = rejectLocked(handle, reason);
                } else {
                    queue.addLast(handle);
                    usage(key).queued++;
                    refreshQueueLocked();
                    scheduleTimeout(handle);
                    logger.info("Queued execution {} for {} (heavy={}, position={})",
                            handle.id, key.getId(), heavy, handle.position);
                }
            }
        }
        publishReady(started);
        if (reject != null) {
            reject.run();
        }
        return handle;
    }

    public int runningCount() {
        return running.get();
    }

    public int heavyRunningCount() {
        return heavyRunning.get();
    }

    public int queuedCount() {
        synchronized (lock) {
            return queue.size();
        }
    }

    public long retryHintSeconds() {
        return Math.max(1, config.getEstimatedSlotMs() / 1000);
    }

    public void cancel(Handle handle) {
        if (handle == null) {
            return;
        }
        boolean queued;
        synchronized (lock) {
            queued = handle.state == State.QUEUED && queue.remove(handle);
            if (queued) {
                usage(handle.client).queued = Math.max(0, usage(handle.client).queued - 1);
                handle.state = State.CANCELLED;
                refreshQueueLocked();
            }
        }
        handle.ready.completeExceptionally(new Cancellation());
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdownNow();
        synchronized (lock) {
            for (Handle handle : queue) {
                handle.ready.completeExceptionally(new Cancellation());
            }
            queue.clear();
        }
    }

    private boolean tryStartLocked(Handle handle, List<Ready> started) {
        if (!canStart(handle)) {
            return false;
        }
        ClientUsage usage = usage(handle.client);
        usage.running++;
        running.incrementAndGet();
        if (handle.heavy) {
            heavyRunning.incrementAndGet();
        }
        handle.state = State.RUNNING;
        handle.position = 0;
        handle.estimatedWaitMs = 0;
        long startedAt = System.nanoTime();
        Lease lease = new Lease(() -> release(handle, startedAt));
        started.add(new Ready(handle, lease));
        logger.debug("Started execution {} for {} (running={}/{})",
                handle.id, handle.client.getId(), running.get(), maxConcurrent());
        return true;
    }

    private boolean canStart(Handle handle) {
        if (running.get() >= maxConcurrent()) {
            return false;
        }
        if (handle.heavy && heavyRunning.get() >= maxHeavyConcurrent()) {
            return false;
        }
        ClientUsage usage = clients.get(handle.client.getId());
        int alreadyRunning = usage == null ? 0 : usage.running;
        return alreadyRunning < maxConcurrentPerClient();
    }

    private String enqueueRejectReason(Handle handle) {
        if (queue.size() >= maxQueue()) {
            return AdmissionRejectedException.QUEUE_FULL;
        }
        if (handle.heavy && heavyQueuedLocked() >= maxHeavyQueue()) {
            return AdmissionRejectedException.QUEUE_FULL;
        }
        ClientUsage usage = clients.get(handle.client.getId());
        int alreadyQueued = usage == null ? 0 : usage.queued;
        if (alreadyQueued >= maxQueuedPerClient()) {
            return AdmissionRejectedException.CLIENT_BUSY;
        }
        return null;
    }

    private int heavyQueuedLocked() {
        int count = 0;
        for (Handle handle : queue) {
            if (handle.heavy) {
                count++;
            }
        }
        return count;
    }

    private Runnable rejectLocked(Handle handle, String reason) {
        handle.state = State.REJECTED;
        handle.rejectReason = reason;
        handle.retryAfterSeconds = retryHintSeconds();
        AdmissionRejectedException exception = AdmissionRejectedException.QUEUE_FULL.equals(reason)
                ? AdmissionRejectedException.queueFull(handle.retryAfterSeconds)
                : AdmissionRejectedException.clientBusy(handle.retryAfterSeconds);
        handle.rejection = exception;
        handle.rejectMessage = exception.getMessage();
        return () -> handle.ready.completeExceptionally(exception);
    }

    private void scheduleTimeout(Handle handle) {
        long timeout = Math.max(1, config.getQueueTimeoutMs());
        ScheduledFuture<?> future = scheduler.schedule(() -> timeout(handle), timeout, TimeUnit.MILLISECONDS);
        handle.ready.whenComplete((lease, error) -> future.cancel(false));
    }

    private void timeout(Handle handle) {
        synchronized (lock) {
            if (handle.state != State.QUEUED || !queue.remove(handle)) {
                return;
            }
            usage(handle.client).queued = Math.max(0, usage(handle.client).queued - 1);
            handle.state = State.REJECTED;
            handle.rejectReason = AdmissionRejectedException.QUEUE_TIMEOUT;
            handle.retryAfterSeconds = retryHintSeconds();
            handle.rejection = AdmissionRejectedException.queueTimeout(handle.retryAfterSeconds);
            handle.rejectMessage = handle.rejection.getMessage();
            refreshQueueLocked();
        }
        handle.ready.completeExceptionally(handle.rejection);
        for (Listener listener : handle.snapshotListeners()) {
            listener.onRejected(handle.rejectMessage, handle.retryAfterSeconds, handle.rejectReason);
        }
        logger.info("Queue timeout for execution {}", handle.id);
    }

    private void release(Handle handle, long startedAtNanos) {
        long elapsedMs = Math.max(1, (System.nanoTime() - startedAtNanos) / 1_000_000);
        recordDuration(handle.heavy, elapsedMs);
        List<Ready> started = new ArrayList<>();
        synchronized (lock) {
            running.updateAndGet(value -> Math.max(0, value - 1));
            if (handle.heavy) {
                heavyRunning.updateAndGet(value -> Math.max(0, value - 1));
            }
            ClientUsage usage = usage(handle.client);
            usage.running = Math.max(0, usage.running - 1);
            dispatchLocked(started);
            refreshQueueLocked();
        }
        publishReady(started);
        logger.debug("Released execution {} after {}ms (running={})", handle.id, elapsedMs, running.get());
    }

    private void dispatchLocked(List<Ready> started) {
        Iterator<Handle> iterator = queue.iterator();
        while (iterator.hasNext()) {
            Handle next = iterator.next();
            if (!canStart(next)) {
                continue;
            }
            iterator.remove();
            usage(next.client).queued = Math.max(0, usage(next.client).queued - 1);
            tryStartLocked(next, started);
        }
    }

    private static void publishReady(List<Ready> started) {
        for (Ready ready : started) {
            ready.handle.ready.complete(ready.lease);
        }
    }

    private void refreshQueueLocked() {
        int position = 1;
        long lightWait = 0;
        long heavyWait = 0;
        long lightSlot = Math.max(1, averageMs(false));
        long heavySlot = Math.max(1, averageMs(true));
        List<Handle> snapshot = List.copyOf(queue);
        for (Handle handle : snapshot) {
            handle.position = position++;
            if (handle.heavy) {
                heavyWait += heavySlot;
                handle.estimatedWaitMs = heavyWait;
            } else {
                lightWait += lightSlot;
                handle.estimatedWaitMs = lightWait;
            }
        }
        scheduler.execute(() -> notifyQueue(snapshot));
    }

    private void notifyQueue(List<Handle> snapshot) {
        for (Handle handle : snapshot) {
            if (handle.state != State.QUEUED) {
                continue;
            }
            for (Listener listener : handle.snapshotListeners()) {
                listener.onUpdate(handle.position, handle.estimatedWaitMs);
            }
        }
    }

    private void recordDuration(boolean heavy, long ms) {
        AtomicLong avg = heavy ? heavyAvgMs : lightAvgMs;
        long previous = avg.get();
        long next = previous == 0 ? ms : (previous * 3 + ms) / 4;
        avg.set(next);
    }

    private long averageMs(boolean heavy) {
        long observed = heavy ? heavyAvgMs.get() : lightAvgMs.get();
        long configured = Math.max(1, config.getEstimatedSlotMs());
        return observed == 0 ? configured : Math.max(configured / 2, observed);
    }

    private ClientUsage usage(ClientKey client) {
        return clients.computeIfAbsent(client.getId(), ignored -> new ClientUsage());
    }

    private int maxConcurrent() {
        return Math.max(1, config.getMaxConcurrent());
    }

    private int maxHeavyConcurrent() {
        return Math.max(1, Math.min(config.getMaxHeavyConcurrent(), maxConcurrent()));
    }

    private int maxQueue() {
        return Math.max(0, config.getMaxQueue());
    }

    private int maxHeavyQueue() {
        return Math.max(0, config.getMaxHeavyQueue());
    }

    private int maxConcurrentPerClient() {
        return Math.max(1, config.getMaxConcurrentPerClient());
    }

    private int maxQueuedPerClient() {
        return Math.max(0, config.getMaxQueuedPerClient());
    }

    static boolean isHeavy(String language) {
        if (language == null || language.isBlank()) {
            return false;
        }
        try {
            return Language.fromId(language).isHeavy();
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static final class ClientUsage {
        int running;
        int queued;
    }

    private record Ready(Handle handle, Lease lease) {
    }
}
