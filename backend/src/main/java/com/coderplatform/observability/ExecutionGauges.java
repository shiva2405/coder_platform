package com.coderplatform.observability;

import com.coderplatform.service.ExecutionAdmissionService;
import com.coderplatform.service.LiveExecutionService;
import com.coderplatform.service.QueuedWorkService;
import com.coderplatform.service.SnippetRateLimiter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

@Component
public class ExecutionGauges {

    public static final String RUNNING = "coder.execution.running";
    public static final String HEAVY_RUNNING = "coder.execution.heavy.running";
    public static final String QUEUED = "coder.execution.queued";
    public static final String JOBS_CACHED = "coder.execution.jobs.cached";
    public static final String LIVE_ACTIVE = "coder.execution.live.active";
    public static final String LIVE_PENDING = "coder.execution.live.pending";
    public static final String RATE_LIMIT_CACHE = "coder.ratelimit.cache.size";

    private final MeterRegistry registry;
    private final ExecutionAdmissionService admission;
    private final QueuedWorkService queuedWorkService;
    private final LiveExecutionService liveExecutionService;
    private final SnippetRateLimiter rateLimiter;

    public ExecutionGauges(
            MeterRegistry registry,
            ExecutionAdmissionService admission,
            QueuedWorkService queuedWorkService,
            LiveExecutionService liveExecutionService,
            SnippetRateLimiter rateLimiter
    ) {
        this.registry = registry;
        this.admission = admission;
        this.queuedWorkService = queuedWorkService;
        this.liveExecutionService = liveExecutionService;
        this.rateLimiter = rateLimiter;
    }

    @PostConstruct
    void bind() {
        Gauge.builder(RUNNING, admission, ExecutionAdmissionService::runningCount)
                .description("Currently running admitted executions")
                .register(registry);
        Gauge.builder(HEAVY_RUNNING, admission, ExecutionAdmissionService::heavyRunningCount)
                .description("Currently running heavy (compiled) executions")
                .register(registry);
        Gauge.builder(QUEUED, admission, ExecutionAdmissionService::queuedCount)
                .description("Executions waiting for an admission slot")
                .register(registry);
        Gauge.builder(JOBS_CACHED, queuedWorkService, QueuedWorkService::jobCount)
                .description("Buffered execution tickets retained in memory")
                .register(registry);
        Gauge.builder(LIVE_ACTIVE, liveExecutionService, LiveExecutionService::activeCount)
                .description("Active live execution processes")
                .register(registry);
        Gauge.builder(LIVE_PENDING, liveExecutionService, LiveExecutionService::pendingCount)
                .description("Live executions waiting in the admission queue")
                .register(registry);
        Gauge.builder(RATE_LIMIT_CACHE, rateLimiter, SnippetRateLimiter::trackedKeyCount)
                .description("Rate-limit windows currently tracked")
                .register(registry);
    }
}
