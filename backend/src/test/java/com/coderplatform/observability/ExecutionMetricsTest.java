package com.coderplatform.observability;

import com.coderplatform.model.CodeExecutionResponse;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExecutionMetricsTest {

    @Test
    void recordsSuccessWithoutFailureCounter() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ExecutionMetrics metrics = new ExecutionMetrics(registry);

        metrics.record("python", CodeExecutionResponse.success("ok", 25));

        assertThat(registry.counter(ExecutionMetrics.EXECUTIONS, "language", "python", "status", "SUCCESS").count())
                .isEqualTo(1);
        assertThat(registry.find(ExecutionMetrics.FAILURES).counter()).isNull();
        assertThat(registry.find(ExecutionMetrics.DURATION)
                .tag("language", "python")
                .tag("status", "SUCCESS")
                .timer())
                .isNotNull();
        assertThat(registry.find(ExecutionMetrics.DURATION)
                .tag("language", "python")
                .tag("status", "SUCCESS")
                .timer()
                .totalTime(java.util.concurrent.TimeUnit.MILLISECONDS))
                .isEqualTo(25);
    }

    @Test
    void recordsFailuresByStatus() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ExecutionMetrics metrics = new ExecutionMetrics(registry);

        metrics.record("java", CodeExecutionResponse.compileError("syntax", 12));
        metrics.record("java", CodeExecutionResponse.timeout("", 30_000));

        assertThat(registry.counter(ExecutionMetrics.FAILURES, "language", "java", "status", "COMPILE_ERROR").count())
                .isEqualTo(1);
        assertThat(registry.counter(ExecutionMetrics.FAILURES, "language", "java", "status", "TIMEOUT").count())
                .isEqualTo(1);
        assertThat(registry.counter(ExecutionMetrics.EXECUTIONS, "language", "java", "status", "TIMEOUT").count())
                .isEqualTo(1);
    }
}
