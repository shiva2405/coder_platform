package com.coderplatform.service;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.TimeUnit;

/**
 * A running program whose stdin stays open and whose stdout/stderr can be
 * consumed incrementally.
 */
public interface StartedProcess {

    OutputStream stdin();

    InputStream stdout();

    InputStream stderr();

    boolean waitFor(long timeout, TimeUnit unit) throws InterruptedException;

    void destroyForcibly();

    boolean isAlive();

    int exitValue();

    long startedAtNanos();
}
