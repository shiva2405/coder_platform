package com.coderplatform.observability;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledOnOs({OS.MAC, OS.LINUX})
class ExecutionProbeTest {

    @Test
    void probeSucceedsWhenBashCanRun() {
        ExecutionProbe.ProbeResult result = new ExecutionProbe().probe(true);

        assertThat(result.up()).isTrue();
        assertThat(result.runtime()).isEqualTo("bash");
        assertThat(result.detail()).contains("coder-ok");
    }

    @Test
    void cachedResultIsReusedUntilForced() {
        ExecutionProbe probe = new ExecutionProbe();
        ExecutionProbe.ProbeResult first = probe.probe(true);
        ExecutionProbe.ProbeResult cached = probe.probe(false);

        assertThat(cached).isSameAs(first);
    }

    @Test
    void healthIndicatorReportsProbeDetails() {
        ExecutionEnvironmentHealthIndicator indicator = new ExecutionEnvironmentHealthIndicator(new ExecutionProbe());

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("runtime", "bash");
        assertThat(health.getDetails().get("probe").toString()).contains("coder-ok");
    }
}
