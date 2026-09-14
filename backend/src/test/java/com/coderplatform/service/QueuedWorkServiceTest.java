package com.coderplatform.service;

import com.coderplatform.config.ExecutionConfig;
import com.coderplatform.exception.AdmissionRejectedException;
import com.coderplatform.model.WorkState;
import com.coderplatform.model.WorkTicketResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QueuedWorkServiceTest {

    private ExecutionConfig config;
    private ExecutionAdmissionService admission;
    private QueuedWorkService jobs;

    @BeforeEach
    void setUp() {
        config = new ExecutionConfig();
        config.setMaxConcurrent(2);
        config.setMaxHeavyConcurrent(1);
        config.setMaxQueue(6);
        config.setMaxHeavyQueue(3);
        config.setMaxConcurrentPerClient(1);
        config.setMaxQueuedPerClient(2);
        config.setQueueTimeoutMs(2_000);
        config.setEstimatedSlotMs(50);
        config.setTicketTtlMs(5_000);
        admission = new ExecutionAdmissionService(config);
        jobs = new QueuedWorkService(admission, config);
    }

    @AfterEach
    void tearDown() {
        if (jobs != null) {
            jobs.shutdown();
        }
        if (admission != null) {
            admission.shutdown();
        }
    }

    @Test
    void doesNotHoldTheCallerWhileQueuedWorkRuns() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        WorkTicketResponse first = jobs.submit("python", ClientKey.anonymous("10.0.0.1"), () -> {
            started.countDown();
            assertThat(release.await(3, TimeUnit.SECONDS)).isTrue();
            return "one";
        });
        assertThat(started.await(1, TimeUnit.SECONDS)).isTrue();

        long began = System.nanoTime();
        WorkTicketResponse second = jobs.submit("python", ClientKey.anonymous("10.0.0.2"), () -> "two");
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - began);

        assertThat(elapsedMs).isLessThan(250);
        assertThat(second.getState()).isIn(WorkState.QUEUED, WorkState.RUNNING);
        if (second.getState() == WorkState.QUEUED) {
            assertThat(second.getPosition()).isEqualTo(1);
            assertThat(second.getEstimatedWaitMs()).isNotNull();
        }

        release.countDown();
        assertThat(awaitState(first.getId(), WorkState.COMPLETED).getResult()).isEqualTo("one");
        assertThat(awaitState(second.getId(), WorkState.COMPLETED).getResult()).isEqualTo("two");
    }

    @Test
    void rejectsCleanlyWhenTheQueueIsSaturated() throws Exception {
        config.setMaxConcurrent(1);
        config.setMaxQueue(1);
        jobs.shutdown();
        admission.shutdown();
        admission = new ExecutionAdmissionService(config);
        jobs = new QueuedWorkService(admission, config);

        CountDownLatch hold = new CountDownLatch(1);
        jobs.submit("python", ClientKey.anonymous("10.0.0.1"), () -> {
            hold.await(3, TimeUnit.SECONDS);
            return "run";
        });
        WorkTicketResponse queued = jobs.submit("python", ClientKey.anonymous("10.0.0.2"), () -> "queued");
        assertThat(queued.getState()).isEqualTo(WorkState.QUEUED);

        assertThatThrownBy(() -> jobs.submit("python", ClientKey.anonymous("10.0.0.3"), () -> "nope"))
                .isInstanceOf(AdmissionRejectedException.class)
                .hasMessageContaining("queue is full");

        hold.countDown();
        assertThat(awaitState(queued.getId(), WorkState.COMPLETED).getResult()).isEqualTo("queued");
    }

    @Test
    void heavyLoadKeepsOtherClientsResponsive() throws Exception {
        config.setMaxConcurrent(3);
        config.setMaxConcurrentPerClient(1);
        config.setMaxQueuedPerClient(1);
        config.setMaxQueue(8);
        jobs.shutdown();
        admission.shutdown();
        admission = new ExecutionAdmissionService(config);
        jobs = new QueuedWorkService(admission, config);

        int attackers = 12;
        CyclicBarrier barrier = new CyclicBarrier(attackers + 1);
        AtomicInteger rejected = new AtomicInteger();
        AtomicInteger completed = new AtomicInteger();
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < attackers; i++) {
            Thread thread = new Thread(() -> {
                try {
                    barrier.await(2, TimeUnit.SECONDS);
                    jobs.submit("python", ClientKey.anonymous("203.0.113.10"), () -> {
                        Thread.sleep(80);
                        return "x";
                    });
                    completed.incrementAndGet();
                } catch (AdmissionRejectedException e) {
                    rejected.incrementAndGet();
                } catch (Exception e) {
                    rejected.incrementAndGet();
                }
            });
            threads.add(thread);
            thread.start();
        }

        barrier.await(2, TimeUnit.SECONDS);
        WorkTicketResponse other = jobs.submit("python", ClientKey.anonymous("198.51.100.7"), () -> "ok");
        assertThat(awaitState(other.getId(), WorkState.COMPLETED).getResult()).isEqualTo("ok");

        for (Thread thread : threads) {
            thread.join(3_000);
        }
        assertThat(rejected.get()).isGreaterThan(0);
        assertThat(completed.get()).isLessThan(attackers);
    }

    private WorkTicketResponse awaitState(String id, WorkState expected) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        WorkTicketResponse current = jobs.get(id);
        while (current.getState() != expected && System.nanoTime() < deadline) {
            Thread.sleep(15);
            current = jobs.get(id);
        }
        assertThat(current.getState()).isEqualTo(expected);
        return current;
    }
}
