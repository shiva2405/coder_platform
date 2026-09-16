package com.coderplatform.observability;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("executionEnvironment")
public class ExecutionEnvironmentHealthIndicator implements HealthIndicator {

    private final ExecutionProbe probe;

    public ExecutionEnvironmentHealthIndicator(ExecutionProbe probe) {
        this.probe = probe;
    }

    @Override
    public Health health() {
        ExecutionProbe.ProbeResult result = probe.probe();
        if (result.up()) {
            return Health.up()
                    .withDetail("runtime", result.runtime())
                    .withDetail("probe", result.detail())
                    .build();
        }
        return Health.down()
                .withDetail("runtime", result.runtime())
                .withDetail("error", result.detail())
                .build();
    }
}
