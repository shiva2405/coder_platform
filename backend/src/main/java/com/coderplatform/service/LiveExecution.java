package com.coderplatform.service;

import com.coderplatform.config.ExecutionConfig;
import com.coderplatform.model.CodeExecutionResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

final class LiveExecution {

    private static final Logger logger = LoggerFactory.getLogger(LiveExecution.class);
    private static final int READ_BUFFER_SIZE = 4096;

    private final String id;
    private final String ownerId;
    private final ExecutionConfig config;
    private final CodeExecutionService executionService;
    private final LiveExecutionListener listener;
    private final Runnable onClosed;
    private final AtomicBoolean completed = new AtomicBoolean(false);
    private final StringBuilder stdout = new StringBuilder();
    private final StringBuilder stderr = new StringBuilder();
    private final Object stdinLock = new Object();

    private volatile PreparedProgram program;
    private volatile StartedProcess process;
    private volatile ScheduledFuture<?> timeoutTask;
    private volatile boolean stdinClosed;

    LiveExecution(
            String id,
            String ownerId,
            ExecutionConfig config,
            CodeExecutionService executionService,
            LiveExecutionListener listener,
            Runnable onClosed
    ) {
        this.id = id;
        this.ownerId = ownerId;
        this.config = config;
        this.executionService = executionService;
        this.listener = listener;
        this.onClosed = onClosed;
    }

    String getId() {
        return id;
    }

    String getOwnerId() {
        return ownerId;
    }

    boolean isCompleted() {
        return completed.get();
    }

    void execute(String language, String code, String initialStdin, ScheduledExecutorService scheduler) {
        try {
            program = executionService.prepare(language, code);
            if (completed.get()) {
                finish(stoppedResponse());
                return;
            }

            CompileResult compileResult = program.compile();
            if (completed.get()) {
                finish(stoppedResponse());
                return;
            }
            if (!compileResult.isSuccess()) {
                finish(compileResult.getErrorResponse());
                return;
            }

            StartedProcess started = program.start(config.getMemoryLimit());
            this.process = started;
            if (completed.get()) {
                started.destroyForcibly();
                finish(stoppedResponse());
                return;
            }

            Thread stdoutReader = startReader(started.stdout(), true);
            Thread stderrReader = startReader(started.stderr(), false);

            if (initialStdin != null && !initialStdin.isEmpty()) {
                writeStdin(initialStdin);
            }

            timeoutTask = scheduler.schedule(this::onTimeout, config.getTimeout(), TimeUnit.MILLISECONDS);

            boolean finished = started.waitFor(config.getTimeout() + 1_000, TimeUnit.MILLISECONDS);
            stdoutReader.join(1_000);
            stderrReader.join(1_000);

            if (completed.get()) {
                return;
            }
            if (!finished || started.isAlive()) {
                finish(CodeExecutionResponse.timeout(limited(stdout), elapsedMs()));
                return;
            }
            finish(toResponse(started));
        } catch (IllegalArgumentException e) {
            finish(CodeExecutionResponse.error("Unsupported language: " + e.getMessage()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            finish(stoppedResponse());
        } catch (Exception e) {
            logger.error("Live execution {} failed", id, e);
            finish(CodeExecutionResponse.error("Execution failed: " + e.getMessage()));
        }
    }

    void writeStdin(String data) throws IOException {
        StartedProcess running = process;
        if (running == null || !running.isAlive() || stdinClosed || completed.get()) {
            throw new IllegalStateException("Process is not accepting input");
        }
        synchronized (stdinLock) {
            if (stdinClosed || completed.get()) {
                throw new IllegalStateException("Process is not accepting input");
            }
            OutputStream in = running.stdin();
            in.write(data.getBytes(StandardCharsets.UTF_8));
            in.flush();
        }
    }

    void closeStdin() throws IOException {
        StartedProcess running = process;
        if (running == null || stdinClosed) {
            return;
        }
        synchronized (stdinLock) {
            if (stdinClosed) {
                return;
            }
            stdinClosed = true;
            try {
                running.stdin().close();
            } catch (IOException e) {
                if (running.isAlive()) {
                    throw e;
                }
            }
        }
    }

    void stop() {
        finish(stoppedResponse(), true);
    }

    void abandon() {
        finish(stoppedResponse(), false);
    }

    private void onTimeout() {
        finish(CodeExecutionResponse.timeout(limited(stdout), elapsedMs()), true);
    }

    private void finish(CodeExecutionResponse response) {
        finish(response, true);
    }

    private void finish(CodeExecutionResponse response, boolean notify) {
        if (!completed.compareAndSet(false, true)) {
            return;
        }
        if (timeoutTask != null) {
            timeoutTask.cancel(false);
        }
        destroyProcess();
        closeProgram();
        try {
            onClosed.run();
        } catch (Exception e) {
            logger.warn("Failed to unregister live execution {}", id, e);
        }
        if (!notify) {
            return;
        }
        try {
            listener.onCompleted(response);
        } catch (Exception e) {
            logger.warn("Live execution listener failed for {}", id, e);
        }
    }

    private CodeExecutionResponse toResponse(StartedProcess started) {
        String out = limited(stdout);
        String err = limited(stderr);
        if (isMemoryExceeded(err)) {
            return CodeExecutionResponse.memoryExceeded(out, elapsedMs(started));
        }
        if (started.exitValue() != 0) {
            return CodeExecutionResponse.runtimeError(out, err, elapsedMs(started));
        }
        return CodeExecutionResponse.success(out, elapsedMs(started));
    }

    private CodeExecutionResponse stoppedResponse() {
        return CodeExecutionResponse.stopped(limited(stdout), limited(stderr), elapsedMs());
    }

    private Thread startReader(InputStream stream, boolean standardOut) {
        Thread thread = new Thread(() -> {
            byte[] buffer = new byte[READ_BUFFER_SIZE];
            try {
                int read;
                while ((read = stream.read(buffer)) != -1) {
                    if (read == 0) {
                        continue;
                    }
                    appendChunk(new String(buffer, 0, read, StandardCharsets.UTF_8), standardOut);
                }
            } catch (IOException e) {
                if (!completed.get() && e.getMessage() != null && !e.getMessage().contains("Stream closed")) {
                    logger.error("Error reading {} for {}", standardOut ? "stdout" : "stderr", id, e);
                }
            }
        }, (standardOut ? "live-stdout-" : "live-stderr-") + id);
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    private void appendChunk(String chunk, boolean standardOut) {
        if (chunk == null || chunk.isEmpty() || completed.get()) {
            return;
        }
        String emit;
        synchronized (this) {
            StringBuilder target = standardOut ? stdout : stderr;
            long max = config.getMaxOutputSize();
            if (target.length() >= max) {
                return;
            }
            int remaining = (int) Math.min(Integer.MAX_VALUE, max - target.length());
            if (chunk.length() > remaining) {
                emit = chunk.substring(0, remaining) + "\n... (output truncated)";
                target.append(emit);
            } else {
                emit = chunk;
                target.append(chunk);
            }
        }
        try {
            if (standardOut) {
                listener.onStdout(emit);
            } else {
                listener.onStderr(emit);
            }
        } catch (Exception e) {
            logger.warn("Failed to stream {} for {}", standardOut ? "stdout" : "stderr", id, e);
        }
    }

    private void destroyProcess() {
        StartedProcess running = process;
        if (running != null) {
            running.destroyForcibly();
        }
        try {
            closeStdin();
        } catch (IOException ignored) {
            // Process is already going away
        }
    }

    private void closeProgram() {
        PreparedProgram prepared = program;
        if (prepared != null) {
            try {
                prepared.close();
            } catch (Exception e) {
                logger.warn("Failed to close workspace for live execution {}", id, e);
            }
        }
    }

    private String limited(StringBuilder buffer) {
        String value = buffer.toString();
        if (value.length() > config.getMaxOutputSize()) {
            return value.substring(0, (int) config.getMaxOutputSize()) + "\n... (output truncated)";
        }
        return value;
    }

    private long elapsedMs() {
        StartedProcess running = process;
        if (running == null) {
            return 0;
        }
        return elapsedMs(running);
    }

    private static long elapsedMs(StartedProcess started) {
        return Math.max(0, (System.nanoTime() - started.startedAtNanos()) / 1_000_000);
    }

    private static boolean isMemoryExceeded(String error) {
        return error.contains("OutOfMemoryError")
                || error.contains("Cannot allocate memory")
                || error.contains("Too small maximum heap");
    }
}
