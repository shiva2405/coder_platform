package com.coderplatform.service;

import com.coderplatform.config.ExecutionConfig;
import com.coderplatform.exception.AdmissionRejectedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ExecutionAdmissionServiceTest {

    private ExecutionConfig config;
    private ExecutionAdmissionService admission;

    @BeforeEach
    void setUp() {
        config = new ExecutionConfig();
        config.setMaxConcurrent(2);
        config.setMaxHeavyConcurrent(1);
        config.setMaxQueue(4);
        config.setMaxHeavyQueue(2);
        config.setMaxConcurrentPerClient(1);
        config.setMaxQueuedPerClient(2);
        config.setQueueTimeoutMs(400);
        config.setEstimatedSlotMs(100);
        admission = new ExecutionAdmissionService(config);
    }

    @AfterEach
    void tearDown() {
        if (admission != null) {
            admission.shutdown();
        }
    }

    @Test
    void startsImmediatelyWhenASlotIsFree() throws Exception {
        ExecutionAdmissionService.Handle handle = admission.submit("python", ClientKey.anonymous("10.0.0.1"));
        assertThat(handle.state()).isEqualTo(ExecutionAdmissionService.State.RUNNING);
        assertThat(handle.whenReady()).succeedsWithin(200, TimeUnit.MILLISECONDS);
        handle.whenReady().get().close();
        assertThat(admission.runningCount()).isZero();
    }

    @Test
    void queuesOverflowAndReportsPosition() throws Exception {
        ExecutionAdmissionService.Handle first = admission.submit("python", ClientKey.anonymous("10.0.0.1"));
        ExecutionAdmissionService.Handle second = admission.submit("python", ClientKey.anonymous("10.0.0.2"));
        ExecutionAdmissionService.Handle third = admission.submit("python", ClientKey.anonymous("10.0.0.3"));

        assertThat(first.state()).isEqualTo(ExecutionAdmissionService.State.RUNNING);
        assertThat(second.state()).isEqualTo(ExecutionAdmissionService.State.RUNNING);
        assertThat(third.state()).isEqualTo(ExecutionAdmissionService.State.QUEUED);
        assertThat(third.position()).isEqualTo(1);
        assertThat(third.estimatedWaitMs()).isPositive();

        first.whenReady().get().close();
        assertThat(third.whenReady()).succeedsWithin(1, TimeUnit.SECONDS);
        assertThat(third.state()).isEqualTo(ExecutionAdmissionService.State.RUNNING);

        second.whenReady().get().close();
        third.whenReady().get().close();
    }

    @Test
    void rejectsWhenQueueIsFull() {
        holdSlots(2);
        ExecutionAdmissionService.Handle queued1 = admission.submit("python", ClientKey.anonymous("10.0.0.3"));
        ExecutionAdmissionService.Handle queued2 = admission.submit("python", ClientKey.anonymous("10.0.0.4"));
        ExecutionAdmissionService.Handle queued3 = admission.submit("python", ClientKey.anonymous("10.0.0.5"));
        ExecutionAdmissionService.Handle queued4 = admission.submit("python", ClientKey.anonymous("10.0.0.6"));
        assertThat(queued1.isRejected()).isFalse();
        assertThat(queued2.isRejected()).isFalse();
        assertThat(queued3.isRejected()).isFalse();
        assertThat(queued4.isRejected()).isFalse();

        ExecutionAdmissionService.Handle overflow = admission.submit("python", ClientKey.anonymous("10.0.0.7"));
        assertThat(overflow.isRejected()).isTrue();
        assertThat(overflow.rejection()).isInstanceOf(AdmissionRejectedException.class);
        assertThat(overflow.rejection().getReason()).isEqualTo(AdmissionRejectedException.QUEUE_FULL);
        assertThat(overflow.rejection().getRetryAfterSeconds()).isPositive();
    }

    @Test
    void heavyLanguagesCannotConsumeEverySlot() throws Exception {
        ExecutionAdmissionService.Handle javaOne = admission.submit("java", ClientKey.anonymous("10.0.0.1"));
        ExecutionAdmissionService.Handle javaTwo = admission.submit("java", ClientKey.anonymous("10.0.0.2"));
        ExecutionAdmissionService.Handle python = admission.submit("python", ClientKey.anonymous("10.0.0.3"));

        assertThat(javaOne.state()).isEqualTo(ExecutionAdmissionService.State.RUNNING);
        assertThat(javaTwo.state()).isEqualTo(ExecutionAdmissionService.State.QUEUED);
        assertThat(python.state()).isEqualTo(ExecutionAdmissionService.State.RUNNING);
        assertThat(admission.heavyRunningCount()).isEqualTo(1);

        javaOne.whenReady().get().close();
        assertThat(javaTwo.whenReady()).succeedsWithin(1, TimeUnit.SECONDS);
        python.whenReady().get().close();
        javaTwo.whenReady().get().close();
    }

    @Test
    void oneClientCannotOccupyEverySlot() {
        ExecutionAdmissionService.Handle first = admission.submit("python", ClientKey.anonymous("10.0.0.8"));
        ExecutionAdmissionService.Handle extraRun = admission.submit("python", ClientKey.anonymous("10.0.0.8"));
        ExecutionAdmissionService.Handle extraQueue1 = admission.submit("python", ClientKey.anonymous("10.0.0.8"));
        ExecutionAdmissionService.Handle extraQueue2 = admission.submit("python", ClientKey.anonymous("10.0.0.8"));
        ExecutionAdmissionService.Handle extraQueue3 = admission.submit("python", ClientKey.anonymous("10.0.0.8"));

        assertThat(first.state()).isEqualTo(ExecutionAdmissionService.State.RUNNING);
        assertThat(extraRun.state()).isEqualTo(ExecutionAdmissionService.State.QUEUED);
        assertThat(extraQueue1.state()).isEqualTo(ExecutionAdmissionService.State.QUEUED);
        assertThat(extraQueue2.isRejected()).isTrue();
        assertThat(extraQueue2.rejection().getReason()).isEqualTo(AdmissionRejectedException.CLIENT_BUSY);
        assertThat(extraQueue3.isRejected()).isTrue();

        ExecutionAdmissionService.Handle other = admission.submit("python", ClientKey.anonymous("10.0.0.9"));
        assertThat(other.state()).isEqualTo(ExecutionAdmissionService.State.RUNNING);
    }

    @Test
    void queueTimeoutRejectsStaleWaiters() throws Exception {
        holdSlots(2);
        ExecutionAdmissionService.Handle queued = admission.submit("python", ClientKey.anonymous("10.0.0.10"));
        assertThat(queued.state()).isEqualTo(ExecutionAdmissionService.State.QUEUED);
        assertThat(queued.whenReady()).failsWithin(1, TimeUnit.SECONDS);
        assertThat(queued.rejection().getReason()).isEqualTo(AdmissionRejectedException.QUEUE_TIMEOUT);
    }

    @Test
    void cancelRemovesQueuedWorkAndPromotesTheNextJob() throws Exception {
        holdSlots(2);
        ExecutionAdmissionService.Handle firstQueued = admission.submit("python", ClientKey.anonymous("10.0.0.11"));
        ExecutionAdmissionService.Handle secondQueued = admission.submit("python", ClientKey.anonymous("10.0.0.12"));
        assertThat(firstQueued.position()).isEqualTo(1);
        assertThat(secondQueued.position()).isEqualTo(2);

        firstQueued.cancel();
        assertThat(firstQueued.whenReady()).failsWithin(200, TimeUnit.MILLISECONDS);
        assertThat(secondQueued.position()).isEqualTo(1);
    }

    @Test
    void concurrentSubmitNeverExceedsConfiguredSlots() throws Exception {
        config.setMaxConcurrent(3);
        config.setMaxConcurrentPerClient(3);
        config.setMaxQueue(50);
        admission.shutdown();
        admission = new ExecutionAdmissionService(config);

        int clients = 20;
        CountDownLatch started = new CountDownLatch(clients);
        AtomicInteger peak = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        List<Thread> threads = new ArrayList<>();

        for (int i = 0; i < clients; i++) {
            String ip = "198.51.100." + (i + 1);
            Thread thread = new Thread(() -> {
                ExecutionAdmissionService.Handle handle = admission.submit("python", ClientKey.anonymous(ip));
                started.countDown();
                if (handle.isRejected()) {
                    rejected.incrementAndGet();
                    return;
                }
                try {
                    ExecutionAdmissionService.Lease lease = handle.whenReady().get(2, TimeUnit.SECONDS);
                    peak.accumulateAndGet(admission.runningCount(), Math::max);
                    Thread.sleep(40);
                    lease.close();
                } catch (Exception e) {
                    rejected.incrementAndGet();
                }
            });
            threads.add(thread);
            thread.start();
        }

        assertThat(started.await(3, TimeUnit.SECONDS)).isTrue();
        for (Thread thread : threads) {
            thread.join(3_000);
        }
        assertThat(peak.get()).isLessThanOrEqualTo(3);
        assertThat(admission.runningCount()).isZero();
        assertThat(rejected.get()).isZero();
    }

    private void holdSlots(int count) {
        for (int i = 0; i < count; i++) {
            ExecutionAdmissionService.Handle handle = admission.submit("python", ClientKey.anonymous("192.0.2." + (i + 1)));
            assertThat(handle.state()).isEqualTo(ExecutionAdmissionService.State.RUNNING);
        }
    }
}
