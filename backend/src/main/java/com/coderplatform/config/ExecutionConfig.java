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
    
    public String getTempDirectory() {
        return tempDirectory;
    }
    
    public void setTempDirectory(String tempDirectory) {
        this.tempDirectory = tempDirectory;
    }
}
