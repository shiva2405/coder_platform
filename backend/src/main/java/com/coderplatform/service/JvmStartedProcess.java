package com.coderplatform.service;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.TimeUnit;

final class JvmStartedProcess implements StartedProcess {

    private final Process process;
    private final long startedAtNanos;

    JvmStartedProcess(Process process, long startedAtNanos) {
        this.process = process;
        this.startedAtNanos = startedAtNanos;
    }

    @Override
    public OutputStream stdin() {
        return process.getOutputStream();
    }

    @Override
    public InputStream stdout() {
        return process.getInputStream();
    }

    @Override
    public InputStream stderr() {
        return process.getErrorStream();
    }

    @Override
    public boolean waitFor(long timeout, TimeUnit unit) throws InterruptedException {
        return process.waitFor(timeout, unit);
    }

    @Override
    public void destroyForcibly() {
        try {
            process.descendants().forEach(handle -> {
                try {
                    handle.destroyForcibly();
                } catch (Exception ignored) {
                    // Best-effort process-tree cleanup
                }
            });
        } catch (Exception ignored) {
            // Some platforms do not expose descendants
        }
        process.destroyForcibly();
    }

    @Override
    public boolean isAlive() {
        return process.isAlive();
    }

    @Override
    public int exitValue() {
        return process.exitValue();
    }

    @Override
    public long startedAtNanos() {
        return startedAtNanos;
    }
}
