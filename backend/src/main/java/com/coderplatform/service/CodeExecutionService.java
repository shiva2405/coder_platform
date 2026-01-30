package com.coderplatform.service;

import com.coderplatform.config.ExecutionConfig;
import com.coderplatform.model.CodeExecutionRequest;
import com.coderplatform.model.CodeExecutionResponse;
import com.coderplatform.model.Language;
import com.coderplatform.model.LanguageInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    public CodeExecutionService(ExecutionConfig config, LanguageExecutor languageExecutor) {
        this.config = config;
        this.languageExecutor = languageExecutor;
    }

    public CodeExecutionResponse execute(CodeExecutionRequest request) {
        long startTime = System.currentTimeMillis();
        Path workDir = null;

        try {
            Language language = Language.fromId(request.getLanguage());
            
            // Create temporary working directory
            workDir = Files.createTempDirectory("coder-");
            File workDirFile = workDir.toFile();
            
            logger.info("Executing {} code in {}", language, workDir);

            // Write source code to file
            String fileName = languageExecutor.getDefaultFileName(language);
            
            // For Java, extract class name from code
            if (language == Language.JAVA) {
                fileName = extractJavaClassName(request.getCode()) + ".java";
            }
            
            File sourceFile = new File(workDirFile, fileName);
            Files.writeString(sourceFile.toPath(), request.getCode());

            // Compile if necessary
            if (language.isRequiresCompilation()) {
                List<String> compileCmd = languageExecutor.getCompileCommand(language, sourceFile, workDirFile);
                if (!compileCmd.isEmpty()) {
                    ProcessResult compileResult = runProcess(compileCmd, workDirFile, null, config.getTimeout());
                    
                    if (compileResult.exitCode != 0) {
                        long executionTime = System.currentTimeMillis() - startTime;
                        return CodeExecutionResponse.compileError(compileResult.stderr, executionTime);
                    }
                    
                    if (compileResult.timedOut) {
                        long executionTime = System.currentTimeMillis() - startTime;
                        return CodeExecutionResponse.timeout("Compilation timed out", executionTime);
                    }
                }
            }

            // Run the code
            List<String> runCmd = languageExecutor.getRunCommand(language, sourceFile, workDirFile, config.getMemoryLimit());
            ProcessResult runResult = runProcess(runCmd, workDirFile, request.getStdin(), config.getTimeout());
            
            long executionTime = System.currentTimeMillis() - startTime;

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

        } catch (IllegalArgumentException e) {
            logger.error("Invalid language: {}", e.getMessage());
            return CodeExecutionResponse.error("Unsupported language: " + request.getLanguage());
        } catch (Exception e) {
            logger.error("Execution error", e);
            long executionTime = System.currentTimeMillis() - startTime;
            return CodeExecutionResponse.error("Execution failed: " + e.getMessage());
        } finally {
            // Cleanup
            if (workDir != null) {
                try {
                    deleteDirectory(workDir.toFile());
                } catch (Exception e) {
                    logger.warn("Failed to cleanup work directory: {}", workDir, e);
                }
            }
        }
    }

    private String extractJavaClassName(String code) {
        // Simple regex to find public class name
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
            "public\\s+class\\s+(\\w+)"
        );
        java.util.regex.Matcher matcher = pattern.matcher(code);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "Main";
    }

    private ProcessResult runProcess(List<String> command, File workDir, String stdin, long timeoutMs) 
            throws IOException, InterruptedException {
        
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(workDir);
        pb.redirectErrorStream(false);
        
        // Set environment variables for resource limits on Unix
        Map<String, String> env = pb.environment();
        env.put("LANG", "en_US.UTF-8");
        
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

        // Read stdout and stderr
        StringBuilder stdout = new StringBuilder();
        StringBuilder stderr = new StringBuilder();

        Thread stdoutReader = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (stdout.length() < config.getMaxOutputSize()) {
                        stdout.append(line).append("\n");
                    }
                }
            } catch (IOException e) {
                logger.error("Error reading stdout", e);
            }
        });

        Thread stderrReader = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (stderr.length() < config.getMaxOutputSize()) {
                        stderr.append(line).append("\n");
                    }
                }
            } catch (IOException e) {
                logger.error("Error reading stderr", e);
            }
        });

        stdoutReader.start();
        stderrReader.start();

        boolean completed = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);

        if (!completed) {
            process.destroyForcibly();
            stdoutReader.join(1000);
            stderrReader.join(1000);
            return new ProcessResult(-1, stdout.toString(), stderr.toString(), true, false);
        }

        stdoutReader.join(1000);
        stderrReader.join(1000);

        int exitCode = process.exitValue();
        boolean memoryExceeded = stderr.toString().contains("OutOfMemoryError") 
                              || stderr.toString().contains("Cannot allocate memory")
                              || stderr.toString().contains("memory");

        return new ProcessResult(exitCode, stdout.toString(), stderr.toString(), false, memoryExceeded);
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

    private static class ProcessResult {
        final int exitCode;
        final String stdout;
        final String stderr;
        final boolean timedOut;
        final boolean memoryExceeded;

        ProcessResult(int exitCode, String stdout, String stderr, boolean timedOut, boolean memoryExceeded) {
            this.exitCode = exitCode;
            this.stdout = stdout;
            this.stderr = stderr;
            this.timedOut = timedOut;
            this.memoryExceeded = memoryExceeded;
        }
    }
}
