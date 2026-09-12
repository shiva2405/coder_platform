package com.coderplatform.service;

import com.coderplatform.model.CodeExecutionResponse;

/**
 * A source program prepared in a temp workspace. Compile once, then run many
 * test cases against the same compiled output.
 */
public interface PreparedProgram extends AutoCloseable {

    CompileResult compile();

    CodeExecutionResponse run(String stdin, long timeoutMs, long memoryLimitBytes);

    /**
     * Start the compiled program with stdin left open for interactive use.
     * {@link #compile()} must have succeeded first.
     */
    StartedProcess start(long memoryLimitBytes) throws java.io.IOException;

    @Override
    void close();
}
