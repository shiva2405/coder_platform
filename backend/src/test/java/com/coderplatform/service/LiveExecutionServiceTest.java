package com.coderplatform.service;

import com.coderplatform.config.ExecutionConfig;
import com.coderplatform.model.CodeExecutionResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LiveExecutionServiceTest {

    private ExecutionConfig config;
    private ExecutionAdmissionService admission;
    private LiveExecutionService liveService;

    @BeforeEach
    void setUp() {
        config = new ExecutionConfig();
        config.setTimeout(5_000);
        config.setMemoryLimit(128L * 1024 * 1024);
        config.setMaxOutputSize(65_536);
        config.setMaxConcurrent(4);
        config.setMaxHeavyConcurrent(2);
        config.setMaxQueue(8);
        config.setMaxHeavyQueue(4);
        config.setMaxConcurrentPerClient(4);
        config.setMaxQueuedPerClient(4);
        config.setQueueTimeoutMs(5_000);
        admission = new ExecutionAdmissionService(config);
        liveService = new LiveExecutionService(new CodeExecutionService(config, new LanguageExecutor()), config, admission);
    }

    @AfterEach
    void tearDown() {
        if (liveService != null) {
            liveService.shutdown();
        }
        if (admission != null) {
            admission.shutdown();
        }
    }

    @Test
    void streamsOutputBeforeProcessFinishes() throws Exception {
        CollectingListener listener = new CollectingListener();
        liveService.start("owner-stream", "python", """
                import time
                print("first", flush=True)
                time.sleep(0.5)
                print("second", flush=True)
                """, "", listener);

        assertThat(listener.started.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(listener.firstStdout.await(5, TimeUnit.SECONDS)).isTrue();
        long afterFirst = System.nanoTime();
        assertThat(listener.done.await(5, TimeUnit.SECONDS)).isTrue();
        long afterDone = System.nanoTime();

        assertThat(afterDone - afterFirst).isGreaterThan(TimeUnit.MILLISECONDS.toNanos(300));
        assertThat(String.join("", listener.stdout)).contains("first").contains("second");
        assertThat(listener.result.get().getStatus()).isEqualTo(CodeExecutionResponse.Status.SUCCESS);
        assertThat(liveService.activeCount()).isZero();
    }

    @Test
    void acceptsStdinWhileRunning() throws Exception {
        CollectingListener listener = new CollectingListener();
        liveService.start("owner-stdin", "python", """
                import sys
                line = sys.stdin.readline()
                sys.stdout.write("got:" + line)
                sys.stdout.flush()
                """, "", listener);

        assertThat(listener.started.await(5, TimeUnit.SECONDS)).isTrue();
        awaitAcceptingInput("owner-stdin");
        liveService.writeStdin("owner-stdin", "Ada\n");

        assertThat(listener.done.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(listener.result.get().getOutput()).contains("got:Ada");
        assertThat(listener.result.get().getStatus()).isEqualTo(CodeExecutionResponse.Status.SUCCESS);
    }

    @Test
    void stopKillsProcessQuickly() throws Exception {
        CollectingListener listener = new CollectingListener();
        long startedAt = System.nanoTime();
        liveService.start("owner-stop", "python", """
                import time
                while True:
                    time.sleep(0.1)
                """, "", listener);

        assertThat(listener.started.await(5, TimeUnit.SECONDS)).isTrue();
        awaitAcceptingInput("owner-stop");
        liveService.stopOwner("owner-stop");

        assertThat(listener.done.await(2, TimeUnit.SECONDS)).isTrue();
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
        assertThat(listener.result.get().getStatus()).isEqualTo(CodeExecutionResponse.Status.STOPPED);
        assertThat(elapsedMs).isLessThan(2_000);
        assertThat(liveService.activeCount()).isZero();
    }

    @Test
    void disconnectCleansUpWorkspaceAndProcess() throws Exception {
        CollectingListener listener = new CollectingListener();
        liveService.start("owner-leave", "python", """
                import time
                while True:
                    time.sleep(0.1)
                """, "", listener);

        assertThat(listener.started.await(5, TimeUnit.SECONDS)).isTrue();
        awaitAcceptingInput("owner-leave");
        assertThat(liveService.hasOwner("owner-leave")).isTrue();

        liveService.releaseOwner("owner-leave");

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (liveService.activeCount() > 0 && System.nanoTime() < deadline) {
            Thread.sleep(20);
        }
        assertThat(liveService.activeCount()).isZero();
        assertThat(liveService.hasOwner("owner-leave")).isFalse();
        assertThat(listener.result.get()).isNull();
    }

    @Test
    void timeoutStopsLongRunningProgram() throws Exception {
        config.setTimeout(400);
        CollectingListener listener = new CollectingListener();
        liveService.start("owner-timeout", "python", """
                import time
                time.sleep(10)
                """, "", listener);

        assertThat(listener.done.await(3, TimeUnit.SECONDS)).isTrue();
        assertThat(listener.result.get().getStatus()).isEqualTo(CodeExecutionResponse.Status.TIMEOUT);
        assertThat(liveService.activeCount()).isZero();
    }

    @Test
    void truncatesOutputAtConfiguredLimit() throws Exception {
        config.setMaxOutputSize(20);
        CollectingListener listener = new CollectingListener();
        liveService.start("owner-limit", "python", "print('x' * 80, flush=True)", "", listener);

        assertThat(listener.done.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(listener.result.get().getStatus()).isEqualTo(CodeExecutionResponse.Status.SUCCESS);
        assertThat(listener.result.get().getOutput()).contains("(output truncated)");
        assertThat(listener.result.get().getOutput().length()).isLessThan(80);
    }

    @Test
    void queuesWhenConcurrentLimitReached() throws Exception {
        config.setMaxConcurrent(1);
        config.setMaxQueue(2);
        liveService.shutdown();
        admission.shutdown();
        admission = new ExecutionAdmissionService(config);
        liveService = new LiveExecutionService(new CodeExecutionService(config, new LanguageExecutor()), config, admission);

        CollectingListener first = new CollectingListener();
        liveService.start("owner-one", "python", """
                import time
                time.sleep(2)
                """, "", first);
        assertThat(first.started.await(5, TimeUnit.SECONDS)).isTrue();
        awaitAcceptingInput("owner-one");

        CollectingListener second = new CollectingListener();
        liveService.start("owner-two", "python", "print(1)", "", second);
        assertThat(second.queued.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(second.position.get()).isEqualTo(1);
        assertThat(second.started.getCount()).isEqualTo(1);

        liveService.stopOwner("owner-one");
        assertThat(first.done.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(second.started.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(second.done.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(second.result.get().getStatus()).isEqualTo(CodeExecutionResponse.Status.SUCCESS);
    }

    @Test
    void rejectsWhenQueueIsFull() throws Exception {
        config.setMaxConcurrent(1);
        config.setMaxQueue(0);
        liveService.shutdown();
        admission.shutdown();
        admission = new ExecutionAdmissionService(config);
        liveService = new LiveExecutionService(new CodeExecutionService(config, new LanguageExecutor()), config, admission);

        CollectingListener first = new CollectingListener();
        liveService.start("owner-one", "python", """
                import time
                time.sleep(2)
                """, "", first);
        assertThat(first.started.await(5, TimeUnit.SECONDS)).isTrue();
        awaitAcceptingInput("owner-one");

        CollectingListener second = new CollectingListener();
        assertThatThrownBy(() -> liveService.start("owner-two", "python", "print(1)", "", second))
                .isInstanceOf(com.coderplatform.exception.AdmissionRejectedException.class)
                .hasMessageContaining("queue is full");

        liveService.stopOwner("owner-one");
        assertThat(first.done.await(2, TimeUnit.SECONDS)).isTrue();
    }

    private void awaitAcceptingInput(String ownerId) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            try {
                liveService.writeStdin(ownerId, "");
                return;
            } catch (Exception ignored) {
                Thread.sleep(20);
            }
        }
        throw new AssertionError("Process did not start accepting input");
    }

    private static final class CollectingListener implements LiveExecutionListener {
        private final CountDownLatch queued = new CountDownLatch(1);
        private final CountDownLatch started = new CountDownLatch(1);
        private final CountDownLatch firstStdout = new CountDownLatch(1);
        private final CountDownLatch done = new CountDownLatch(1);
        private final List<String> stdout = new CopyOnWriteArrayList<>();
        private final AtomicReference<CodeExecutionResponse> result = new AtomicReference<>();
        private final AtomicReference<Integer> position = new AtomicReference<>();

        @Override
        public void onQueued(String executionId, int queuePosition, long estimatedWaitMs) {
            position.set(queuePosition);
            queued.countDown();
        }

        @Override
        public void onStarted(String executionId) {
            started.countDown();
        }

        @Override
        public void onStdout(String chunk) {
            stdout.add(chunk);
            firstStdout.countDown();
        }

        @Override
        public void onStderr(String chunk) {
            // unused in these cases
        }

        @Override
        public void onCompleted(CodeExecutionResponse response) {
            result.set(response);
            done.countDown();
        }
    }
}
