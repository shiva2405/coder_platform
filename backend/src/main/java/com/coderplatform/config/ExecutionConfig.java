package com.coderplatform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "execution")
public class ExecutionConfig {
    
    private long timeout = 30000;           // 30 seconds default
    private long memoryLimit = 1048576;     // 1MB default
    private long maxOutputSize = 65536;     // 64KB default
    private int maxConcurrentLive = 32;
    private int maxConcurrent = 8;
    private int maxHeavyConcurrent = 3;
    private int maxQueue = 24;
    private int maxHeavyQueue = 8;
    private int maxConcurrentPerClient = 2;
    private int maxQueuedPerClient = 3;
    private long queueTimeoutMs = 20_000;
    private long estimatedSlotMs = 2_000;
    private long ticketTtlMs = 60_000;
    private String tempDirectory = "/tmp/coder-platform";
    
    public long getTimeout() {
        return timeout;
    }
    
    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }
    
    public long getMemoryLimit() {
        return memoryLimit;
    }
    
    public void setMemoryLimit(long memoryLimit) {
        this.memoryLimit = memoryLimit;
    }
    
    public long getMaxOutputSize() {
        return maxOutputSize;
    }
    
    public void setMaxOutputSize(long maxOutputSize) {
        this.maxOutputSize = maxOutputSize;
    }

    public int getMaxConcurrentLive() {
        return maxConcurrentLive;
    }

    public void setMaxConcurrentLive(int maxConcurrentLive) {
        this.maxConcurrentLive = maxConcurrentLive;
    }

    public int getMaxConcurrent() {
        return maxConcurrent;
    }

    public void setMaxConcurrent(int maxConcurrent) {
        this.maxConcurrent = maxConcurrent;
    }

    public int getMaxHeavyConcurrent() {
        return maxHeavyConcurrent;
    }

    public void setMaxHeavyConcurrent(int maxHeavyConcurrent) {
        this.maxHeavyConcurrent = maxHeavyConcurrent;
    }

    public int getMaxQueue() {
        return maxQueue;
    }

    public void setMaxQueue(int maxQueue) {
        this.maxQueue = maxQueue;
    }

    public int getMaxHeavyQueue() {
        return maxHeavyQueue;
    }

    public void setMaxHeavyQueue(int maxHeavyQueue) {
        this.maxHeavyQueue = maxHeavyQueue;
    }

    public int getMaxConcurrentPerClient() {
        return maxConcurrentPerClient;
    }

    public void setMaxConcurrentPerClient(int maxConcurrentPerClient) {
        this.maxConcurrentPerClient = maxConcurrentPerClient;
    }

    public int getMaxQueuedPerClient() {
        return maxQueuedPerClient;
    }

    public void setMaxQueuedPerClient(int maxQueuedPerClient) {
        this.maxQueuedPerClient = maxQueuedPerClient;
    }

    public long getQueueTimeoutMs() {
        return queueTimeoutMs;
    }

    public void setQueueTimeoutMs(long queueTimeoutMs) {
        this.queueTimeoutMs = queueTimeoutMs;
    }

    public long getEstimatedSlotMs() {
        return estimatedSlotMs;
    }

    public void setEstimatedSlotMs(long estimatedSlotMs) {
        this.estimatedSlotMs = estimatedSlotMs;
    }

    public long getTicketTtlMs() {
        return ticketTtlMs;
    }

    public void setTicketTtlMs(long ticketTtlMs) {
        this.ticketTtlMs = ticketTtlMs;
    }
    
    public String getTempDirectory() {
        return tempDirectory;
    }
    
    public void setTempDirectory(String tempDirectory) {
        this.tempDirectory = tempDirectory;
    }
}
