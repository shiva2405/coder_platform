package com.coderplatform.observability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Cheap check that this host can actually spawn a process used by the playground.
 * Results are cached briefly so liveness/readiness probes do not fork on every scrape.
 */
@Component
public class ExecutionProbe {

    private static final Logger logger = LoggerFactory.getLogger(ExecutionProbe.class);
    private static final long CACHE_TTL_MS = 15_000;
    private static final long TIMEOUT_MS = 3_000;

    private final Object lock = new Object();
    private volatile ProbeResult cached;
    private volatile long cachedAt;

    public ProbeResult probe() {
        return probe(false);
    }

    public ProbeResult probe(boolean force) {
        if (!force) {
            ProbeResult current = cached;
            if (current != null && System.currentTimeMillis() - cachedAt < CACHE_TTL_MS) {
                return current;
            }
        }
        synchronized (lock) {
            if (!force) {
                ProbeResult current = cached;
                if (current != null && System.currentTimeMillis() - cachedAt < CACHE_TTL_MS) {
                    return current;
                }
            }
            ProbeResult result = runProbe();
            cached = result;
            cachedAt = System.currentTimeMillis();
            return result;
        }
    }

    private ProbeResult runProbe() {
        List<String> command = List.of("bash", "-lc", "echo coder-ok");
        Process process = null;
        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.redirectErrorStream(true);
            process = builder.start();
            boolean finished = process.waitFor(TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                return ProbeResult.down("Execution probe timed out");
            }
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            if (process.exitValue() != 0 || !output.contains("coder-ok")) {
                return ProbeResult.down("Execution probe failed: " + output);
            }
            return ProbeResult.up("bash", output);
        } catch (IOException e) {
            logger.warn("Execution environment probe failed to start", e);
            return ProbeResult.down("Cannot start execution process: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ProbeResult.down("Execution probe interrupted");
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    public record ProbeResult(boolean up, String runtime, String detail) {
        static ProbeResult up(String runtime, String detail) {
            return new ProbeResult(true, runtime, detail);
        }

        static ProbeResult down(String detail) {
            return new ProbeResult(false, "unavailable", detail);
        }
    }
}
