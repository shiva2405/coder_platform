package com.coderplatform.service;

import com.coderplatform.config.ExecutionConfig;
import com.coderplatform.model.CodeExecutionRequest;
import com.coderplatform.model.CodeExecutionResponse;
import com.coderplatform.model.Language;
import com.coderplatform.model.LanguageInfo;
import com.coderplatform.model.ProjectFile;
import com.coderplatform.observability.ExecutionMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@EnabledOnOs({OS.MAC, OS.LINUX})
class CodeExecutionServiceTest {

    private ExecutionConfig config;
    private SimpleMeterRegistry registry;
    private CodeExecutionService service;

    @BeforeEach
    void setUp() {
        assumeTrue(commandExists("bash"), "bash is required for local execution-service tests");
        config = new ExecutionConfig();
        config.setTimeout(5_000);
        config.setMemoryLimit(128L * 1024 * 1024);
        config.setMaxOutputSize(65_536);
        registry = new SimpleMeterRegistry();
        service = new CodeExecutionService(config, new LanguageExecutor(), new ExecutionMetrics(registry));
    }

    @Test
    void unknownLanguageReturnsError() {
        CodeExecutionResponse response = service.execute(new CodeExecutionRequest("cobol", "DISPLAY 1.", ""));

        assertThat(response.getStatus()).isEqualTo(CodeExecutionResponse.Status.ERROR);
        assertThat(response.getError()).contains("Unsupported language: cobol");
        assertThat(registry.counter("coder.executions", "language", "cobol", "status", "ERROR").count()).isEqualTo(1);
        assertThat(registry.counter("coder.execution.failures", "language", "cobol", "status", "ERROR").count()).isEqualTo(1);
    }

    @Test
    void successfulRunReturnsStdout() {
        CodeExecutionResponse response = service.execute(new CodeExecutionRequest(
                "bash", "echo hello-from-tests", ""));

        assertThat(response.getStatus()).isEqualTo(CodeExecutionResponse.Status.SUCCESS);
        assertThat(response.getOutput()).contains("hello-from-tests");
        assertThat(response.getExecutionTime()).isGreaterThanOrEqualTo(0);
        assertThat(registry.counter("coder.executions", "language", "bash", "status", "SUCCESS").count()).isEqualTo(1);
    }

    @Test
    void stdinIsPassedToTheProgram() {
        CodeExecutionResponse response = service.execute(new CodeExecutionRequest(
                "bash", "read line; echo \"got:$line\"", "stdin-value\n"));

        assertThat(response.getStatus()).isEqualTo(CodeExecutionResponse.Status.SUCCESS);
        assertThat(response.getOutput()).contains("got:stdin-value");
    }

    @Test
    void nonZeroExitIsRuntimeError() {
        CodeExecutionResponse response = service.execute(new CodeExecutionRequest(
                "bash", "echo before-fail; echo boom >&2; exit 2", ""));

        assertThat(response.getStatus()).isEqualTo(CodeExecutionResponse.Status.RUNTIME_ERROR);
        assertThat(response.getOutput()).contains("before-fail");
        assertThat(response.getError()).contains("boom");
    }

    @Test
    void timeoutStatusIsReturnedWhenProcessExceedsLimit() {
        config.setTimeout(400);
        CodeExecutionResponse response = service.execute(new CodeExecutionRequest("bash", "sleep 5", ""));

        assertThat(response.getStatus()).isEqualTo(CodeExecutionResponse.Status.TIMEOUT);
        assertThat(response.getError()).contains("timed out");
    }

    @Test
    void memoryExceededIsDetectedFromStderr() {
        CodeExecutionResponse response = service.execute(new CodeExecutionRequest(
                "bash", "echo leftover; echo OutOfMemoryError: Java heap space >&2; exit 1", ""));

        assertThat(response.getStatus()).isEqualTo(CodeExecutionResponse.Status.MEMORY_EXCEEDED);
        assertThat(response.getError()).contains("Memory limit exceeded");
        assertThat(response.getOutput()).contains("leftover");
    }

    @Test
    void pythonMemoryErrorIsDetected() {
        CodeExecutionResponse response = service.execute(new CodeExecutionRequest(
                "bash", "echo MemoryError: allocation failed >&2; exit 1", ""));

        assertThat(response.getStatus()).isEqualTo(CodeExecutionResponse.Status.MEMORY_EXCEEDED);
    }

    @Test
    void outputIsTruncatedAtConfiguredLimit() {
        config.setMaxOutputSize(80);
        String script = IntStream.rangeClosed(1, 40)
                .mapToObj(i -> "echo line-" + i)
                .collect(Collectors.joining("\n"));

        CodeExecutionResponse response = service.execute(new CodeExecutionRequest("bash", script, ""));

        assertThat(response.getStatus()).isEqualTo(CodeExecutionResponse.Status.SUCCESS);
        assertThat(response.getOutput()).contains("... (output truncated)");
        assertThat(response.getOutput().length()).isLessThan(80 + "... (output truncated)".length() + 5);
    }

    @Test
    void pathTraversalIsRejected() {
        CodeExecutionResponse response = service.execute(new CodeExecutionRequest(
                "python",
                List.of(new ProjectFile("../secret.py", "print(1)")),
                "../secret.py",
                ""
        ));

        assertThat(response.getStatus()).isEqualTo(CodeExecutionResponse.Status.ERROR);
        assertThat(response.getError()).contains("..");
    }

    @Test
    void javaProjectCompilesMultipleFiles() {
        assumeTrue(commandExists("javac") && commandExists("java"), "javac/java required");

        CodeExecutionResponse response = service.execute(new CodeExecutionRequest(
                "java",
                List.of(
                        new ProjectFile("Main.java", """
                                public class Main {
                                    public static void main(String[] args) {
                                        System.out.println(Greeter.hello("world"));
                                    }
                                }
                                """),
                        new ProjectFile("Greeter.java", """
                                public class Greeter {
                                    public static String hello(String name) {
                                        return "hello-" + name;
                                    }
                                }
                                """)
                ),
                "Main.java",
                ""
        ));

        assertThat(response.getStatus()).isEqualTo(CodeExecutionResponse.Status.SUCCESS);
        assertThat(response.getOutput()).contains("hello-world");
    }

    @Test
    void cProjectCompilesSourceAndHeader() {
        assumeTrue(commandExists("gcc"), "gcc is required");

        CodeExecutionResponse response = service.execute(new CodeExecutionRequest(
                "c",
                List.of(
                        new ProjectFile("main.c", """
                                #include <stdio.h>
                                #include "util.h"
                                int main(void) {
                                    printf("%s\\n", greeting());
                                    return 0;
                                }
                                """),
                        new ProjectFile("util.c", """
                                #include "util.h"
                                const char* greeting(void) { return "hello-from-c"; }
                                """),
                        new ProjectFile("util.h", """
                                #pragma once
                                const char* greeting(void);
                                """)
                ),
                "main.c",
                ""
        ));

        assertThat(response.getStatus()).isEqualTo(CodeExecutionResponse.Status.SUCCESS);
        assertThat(response.getOutput()).contains("hello-from-c");
    }

    @Test
    void compileErrorUsesCompilerStderr() {
        assumeTrue(commandExists("javac"), "javac is required for compile-error coverage");

        CodeExecutionResponse response = service.execute(new CodeExecutionRequest(
                "java", "public class Main { this is not java }", ""));

        assertThat(response.getStatus()).isEqualTo(CodeExecutionResponse.Status.COMPILE_ERROR);
        assertThat(response.getError()).isNotBlank();
    }

    @Test
    void supportedLanguagesIncludeEveryLanguageIdAndSample() {
        List<LanguageInfo> languages = service.getSupportedLanguages();

        assertThat(languages).hasSize(Language.values().length);
        assertThat(languages).extracting(LanguageInfo::getId)
                .containsExactlyInAnyOrder(
                        "java", "python", "javascript", "typescript", "c", "cpp", "go",
                        "rust", "ruby", "php", "kotlin", "swift", "perl", "bash"
                );
        assertThat(languages).allSatisfy(language -> {
            assertThat(language.getName()).isNotBlank();
            assertThat(language.getExtension()).startsWith(".");
            assertThat(language.getSampleCode()).isNotBlank();
        });
    }

    private static boolean commandExists(String command) {
        try {
            Process process = new ProcessBuilder("bash", "-lc", "command -v " + command).start();
            return process.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
