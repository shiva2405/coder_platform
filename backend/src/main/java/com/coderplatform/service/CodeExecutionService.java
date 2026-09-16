package com.coderplatform.service;

import com.coderplatform.config.ExecutionConfig;
import com.coderplatform.model.CodeExecutionRequest;
import com.coderplatform.model.CodeExecutionResponse;
import com.coderplatform.model.Language;
import com.coderplatform.model.LanguageInfo;
import com.coderplatform.observability.ExecutionMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
public class CodeExecutionService {

    private static final Logger logger = LoggerFactory.getLogger(CodeExecutionService.class);

    private final ExecutionConfig config;
    private final LanguageExecutor languageExecutor;
    private final ExecutionMetrics metrics;

    public CodeExecutionService(ExecutionConfig config, LanguageExecutor languageExecutor) {
        this(config, languageExecutor, null);
    }

    @Autowired
    public CodeExecutionService(ExecutionConfig config, LanguageExecutor languageExecutor, ExecutionMetrics metrics) {
        this.config = config;
        this.languageExecutor = languageExecutor;
        this.metrics = metrics;
    }

    public CodeExecutionResponse execute(CodeExecutionRequest request) {
        try {
            return execute(resolve(request), request.getStdin());
        } catch (com.coderplatform.exception.InvalidProjectException e) {
            return record(request.getLanguage(), CodeExecutionResponse.error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            String language = request.getLanguage();
            return record(language, CodeExecutionResponse.error("Unsupported language: " + language));
        }
    }

    public CodeExecutionResponse execute(ProjectSources sources, String stdin) {
        String language = sources.getLanguage();
        MDC.put("language", language == null ? "" : language);
        try (PreparedProgram program = prepare(sources)) {
            CompileResult compileResult = program.compile();
            if (!compileResult.isSuccess()) {
                return record(language, compileResult.getErrorResponse());
            }
            return record(language, program.run(stdin, config.getTimeout(), config.getMemoryLimit()));
        } catch (IllegalArgumentException e) {
            logger.error("Invalid language: {}", e.getMessage());
            return record(language, CodeExecutionResponse.error("Unsupported language: " + language));
        } catch (Exception e) {
            logger.error("Execution error", e);
            return record(language, CodeExecutionResponse.error("Execution failed: " + e.getMessage()));
        } finally {
            MDC.remove("language");
        }
    }

    public ProjectSources resolve(CodeExecutionRequest request) {
        return ProjectSources.resolve(
                request.getLanguage(),
                request.getCode(),
                request.getFiles(),
                request.getEntrypoint()
        );
    }

    /**
     * Write source to a temp workspace and return a session that can compile once
     * and then run many times against the same compiled output.
     */
    public PreparedProgram prepare(String languageId, String code) {
        return prepare(ProjectSources.resolve(languageId, code, null, null));
    }

    public PreparedProgram prepare(ProjectSources sources) {
        try {
            return new ExecutionSession(sources);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to prepare program: " + e.getMessage(), e);
        }
    }

    private ProcessResult runProcess(List<String> command, File workDir, String stdin, long timeoutMs)
            throws IOException, InterruptedException {
        return runProcess(command, workDir, stdin, timeoutMs, Map.of());
    }

    private ProcessResult runProcess(
            List<String> command,
            File workDir,
            String stdin,
            long timeoutMs,
            Map<String, String> extraEnv
    ) throws IOException, InterruptedException {
        
        ProcessBuilder pb = processBuilder(command, workDir, extraEnv);
        
        // Read stdout and stderr using dedicated threads with pre-allocated buffers
        StringBuilder stdout = new StringBuilder(4096);
        StringBuilder stderr = new StringBuilder(4096);
        
        // Start timing ONLY when process actually starts
        long processStartTime = System.nanoTime();
        Process process = pb.start();

        // Write stdin if provided
        if (stdin != null && !stdin.isEmpty()) {
            try (OutputStream os = process.getOutputStream()) {
                os.write(stdin.getBytes());
                os.flush();
            }
        } else {
            process.getOutputStream().close();
        }

        Thread stdoutReader = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()), 8192)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (stdout.length() < config.getMaxOutputSize()) {
                        stdout.append(line).append("\n");
                    }
                }
            } catch (IOException e) {
                // Process may have been killed, ignore
                if (!e.getMessage().contains("Stream closed")) {
                    logger.error("Error reading stdout", e);
                }
            }
        }, "stdout-reader");

        Thread stderrReader = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()), 8192)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (stderr.length() < config.getMaxOutputSize()) {
                        stderr.append(line).append("\n");
                    }
                }
            } catch (IOException e) {
                // Process may have been killed, ignore
                if (!e.getMessage().contains("Stream closed")) {
                    logger.error("Error reading stderr", e);
                }
            }
        }, "stderr-reader");

        stdoutReader.start();
        stderrReader.start();

        boolean completed = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
        
        // Measure execution time right after process completes (before thread cleanup)
        long processEndTime = System.nanoTime();
        long actualExecutionTimeMs = (processEndTime - processStartTime) / 1_000_000;

        if (!completed) {
            process.destroyForcibly();
            stdoutReader.join(500);
            stderrReader.join(500);
            return new ProcessResult(-1, stdout.toString(), stderr.toString(), true, false, actualExecutionTimeMs);
        }

        // Wait for reader threads to finish (short timeout since process is done)
        stdoutReader.join(500);
        stderrReader.join(500);

        int exitCode = process.exitValue();
        String errorText = stderr.toString();
        boolean memoryExceeded = errorText.contains("OutOfMemoryError")
                              || errorText.contains("Cannot allocate memory")
                              || errorText.contains("Too small maximum heap")
                              || errorText.contains("MemoryError");

        return new ProcessResult(exitCode, stdout.toString(), stderr.toString(), false, memoryExceeded, actualExecutionTimeMs);
    }

    private static ProcessBuilder processBuilder(List<String> command, File workDir) {
        return processBuilder(command, workDir, Map.of());
    }

    private static ProcessBuilder processBuilder(List<String> command, File workDir, Map<String, String> extraEnv) {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(workDir);
        pb.redirectErrorStream(false);
        Map<String, String> env = pb.environment();
        env.put("LANG", "en_US.UTF-8");
        env.put("PYTHONUNBUFFERED", "1");
        env.put("PYTHONIOENCODING", "UTF-8");
        if (extraEnv != null) {
            env.putAll(extraEnv);
        }
        return pb;
    }

    private String truncateOutput(String output) {
        if (output == null) return "";
        if (output.length() > config.getMaxOutputSize()) {
            return output.substring(0, (int) config.getMaxOutputSize()) 
                   + "\n... (output truncated)";
        }
        return output;
    }

    private void deleteDirectory(File directory) {
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteDirectory(file);
                } else {
                    file.delete();
                }
            }
        }
        directory.delete();
    }

    public List<LanguageInfo> getSupportedLanguages() {
        List<LanguageInfo> languages = new ArrayList<>();
        
        languages.add(new LanguageInfo("java", "Java", ".java", getSampleCode("java")));
        languages.add(new LanguageInfo("python", "Python", ".py", getSampleCode("python")));
        languages.add(new LanguageInfo("javascript", "JavaScript", ".js", getSampleCode("javascript")));
        languages.add(new LanguageInfo("typescript", "TypeScript", ".ts", getSampleCode("typescript")));
        languages.add(new LanguageInfo("c", "C", ".c", getSampleCode("c")));
        languages.add(new LanguageInfo("cpp", "C++", ".cpp", getSampleCode("cpp")));
        languages.add(new LanguageInfo("go", "Go", ".go", getSampleCode("go")));
        languages.add(new LanguageInfo("rust", "Rust", ".rs", getSampleCode("rust")));
        languages.add(new LanguageInfo("ruby", "Ruby", ".rb", getSampleCode("ruby")));
        languages.add(new LanguageInfo("php", "PHP", ".php", getSampleCode("php")));
        languages.add(new LanguageInfo("kotlin", "Kotlin", ".kt", getSampleCode("kotlin")));
        languages.add(new LanguageInfo("swift", "Swift", ".swift", getSampleCode("swift")));
        languages.add(new LanguageInfo("perl", "Perl", ".pl", getSampleCode("perl")));
        languages.add(new LanguageInfo("bash", "Bash", ".sh", getSampleCode("bash")));
        
        return languages;
    }

    private String getSampleCode(String language) {
        switch (language) {
            case "java":
                return "public class Main {\n    public static void main(String[] args) {\n        System.out.println(\"Hello, World!\");\n    }\n}";
            case "python":
                return "print(\"Hello, World!\")";
            case "javascript":
                return "console.log(\"Hello, World!\");";
            case "typescript":
                return "const greeting: string = \"Hello, World!\";\nconsole.log(greeting);";
            case "c":
                return "#include <stdio.h>\n\nint main() {\n    printf(\"Hello, World!\\n\");\n    return 0;\n}";
            case "cpp":
                return "#include <iostream>\n\nint main() {\n    std::cout << \"Hello, World!\" << std::endl;\n    return 0;\n}";
            case "go":
                return "package main\n\nimport \"fmt\"\n\nfunc main() {\n    fmt.Println(\"Hello, World!\")\n}";
            case "rust":
                return "fn main() {\n    println!(\"Hello, World!\");\n}";
            case "ruby":
                return "puts \"Hello, World!\"";
            case "php":
                return "<?php\necho \"Hello, World!\\n\";\n?>";
            case "kotlin":
                return "fun main() {\n    println(\"Hello, World!\")\n}";
            case "swift":
                return "print(\"Hello, World!\")";
            case "perl":
                return "print \"Hello, World!\\n\";";
            case "bash":
                return "#!/bin/bash\necho \"Hello, World!\"";
            default:
                return "// Hello World";
        }
    }

    private final class ExecutionSession implements PreparedProgram {
        private final Language language;
        private final Path workDir;
        private final File workDirFile;
        private final ProjectLayout layout;
        private final String entrypointSource;
        private boolean compiled;
        private boolean closed;

        private ExecutionSession(ProjectSources sources) throws IOException {
            this.language = Language.fromId(sources.getLanguage());
            this.workDir = Files.createTempDirectory("coder-");
            this.workDirFile = workDir.toFile();
            this.layout = sources.materialize(workDir);
            this.entrypointSource = sources.entrypointContent();
            logger.info("Prepared {} project ({} files) in {}", language.getId(), sources.getFiles().size(), workDir);
        }

        @Override
        public CompileResult compile() {
            ensureOpen();
            try {
                if (language.isRequiresCompilation()) {
                    List<String> compileCmd = languageExecutor.getCompileCommand(language, layout, entrypointSource);
                    if (!compileCmd.isEmpty()) {
                        ProcessResult compileResult = runProcess(compileCmd, workDirFile, null, config.getTimeout());
                        if (compileResult.timedOut) {
                            return CompileResult.failure(
                                    CodeExecutionResponse.timeout("Compilation timed out", compileResult.executionTimeMs)
                            );
                        }
                        if (compileResult.exitCode != 0) {
                            return CompileResult.failure(
                                    CodeExecutionResponse.compileError(compileResult.stderr, compileResult.executionTimeMs)
                            );
                        }
                    }
                }
                compiled = true;
                return CompileResult.success();
            } catch (Exception e) {
                logger.error("Compilation error", e);
                return CompileResult.failure(CodeExecutionResponse.error("Compilation failed: " + e.getMessage()));
            }
        }

        @Override
        public CodeExecutionResponse run(String stdin, long timeoutMs, long memoryLimitBytes) {
            ensureOpen();
            if (!compiled) {
                throw new IllegalStateException("compile() must be called before run()");
            }
            try {
                long runTimeout = timeoutMs > 0 ? timeoutMs : config.getTimeout();
                long memoryLimit = memoryLimitBytes > 0 ? memoryLimitBytes : config.getMemoryLimit();
                List<String> runCmd = languageExecutor.getRunCommand(language, layout, entrypointSource, memoryLimit);
                ProcessResult runResult = runProcess(
                        runCmd,
                        workDirFile,
                        stdin,
                        runTimeout,
                        languageExecutor.getRunEnvironment(language, layout)
                );
                long executionTime = runResult.executionTimeMs;

                if (runResult.timedOut) {
                    return CodeExecutionResponse.timeout(truncateOutput(runResult.stdout), executionTime);
                }
                if (runResult.memoryExceeded) {
                    return CodeExecutionResponse.memoryExceeded(truncateOutput(runResult.stdout), executionTime);
                }
                if (runResult.exitCode != 0) {
                    return CodeExecutionResponse.runtimeError(
                            truncateOutput(runResult.stdout),
                            runResult.stderr,
                            executionTime
                    );
                }
                return CodeExecutionResponse.success(truncateOutput(runResult.stdout), executionTime);
            } catch (Exception e) {
                logger.error("Run error", e);
                return CodeExecutionResponse.error("Execution failed: " + e.getMessage());
            }
        }

        @Override
        public StartedProcess start(long memoryLimitBytes) throws IOException {
            ensureOpen();
            if (!compiled) {
                throw new IllegalStateException("compile() must be called before start()");
            }
            long memoryLimit = memoryLimitBytes > 0 ? memoryLimitBytes : config.getMemoryLimit();
            List<String> runCmd = languageExecutor.getRunCommand(language, layout, entrypointSource, memoryLimit);
            ProcessBuilder pb = processBuilder(runCmd, workDirFile, languageExecutor.getRunEnvironment(language, layout));
            long startedAt = System.nanoTime();
            return new JvmStartedProcess(pb.start(), startedAt);
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            try {
                deleteDirectory(workDirFile);
            } catch (Exception e) {
                logger.warn("Failed to cleanup work directory: {}", workDir, e);
            }
        }

        private void ensureOpen() {
            if (closed) {
                throw new IllegalStateException("Prepared program has already been closed");
            }
        }
    }

    private CodeExecutionResponse record(String language, CodeExecutionResponse response) {
        if (metrics != null) {
            metrics.record(language, response);
        }
        if (response != null) {
            logger.info("Execution completed status={} timeMs={}", response.getStatus(), response.getExecutionTime());
        }
        return response;
    }

    private static class ProcessResult {
        final int exitCode;
        final String stdout;
        final String stderr;
        final boolean timedOut;
        final boolean memoryExceeded;
        final long executionTimeMs;  // Actual process execution time

        ProcessResult(int exitCode, String stdout, String stderr, boolean timedOut, boolean memoryExceeded, long executionTimeMs) {
            this.exitCode = exitCode;
            this.stdout = stdout;
            this.stderr = stderr;
            this.timedOut = timedOut;
            this.memoryExceeded = memoryExceeded;
            this.executionTimeMs = executionTimeMs;
        }
    }
}
