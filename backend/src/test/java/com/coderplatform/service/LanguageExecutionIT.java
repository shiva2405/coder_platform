package com.coderplatform.service;

import com.coderplatform.config.ExecutionConfig;
import com.coderplatform.model.CodeExecutionRequest;
import com.coderplatform.model.CodeExecutionResponse;
import com.coderplatform.model.Language;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Optional container/toolchain tests. Excluded from {@code mvn test} unless
 * {@code -Pcontainer-tests} is set and the language runtimes are installed.
 */
@Tag("container")
@Timeout(value = 90, unit = TimeUnit.SECONDS)
class LanguageExecutionIT {

    private ExecutionConfig config;
    private CodeExecutionService service;

    @BeforeEach
    void setUp() {
        config = new ExecutionConfig();
        config.setTimeout(15_000);
        config.setMemoryLimit(128L * 1024 * 1024);
        config.setMaxOutputSize(65_536);
        service = new CodeExecutionService(config, new LanguageExecutor());
    }

    @ParameterizedTest(name = "success {0}")
    @MethodSource("successCases")
    void successfulRun(String language, String code, String expected) {
        assumeLanguage(language);
        CodeExecutionResponse response = service.execute(new CodeExecutionRequest(language, code, ""));
        assertThat(response.getStatus()).isEqualTo(CodeExecutionResponse.Status.SUCCESS);
        assertThat(response.getOutput()).contains(expected);
    }

    @ParameterizedTest(name = "stdin {0}")
    @MethodSource("stdinCases")
    void stdinIsAvailable(String language, String code, String stdin, String expected) {
        assumeLanguage(language);
        CodeExecutionResponse response = service.execute(new CodeExecutionRequest(language, code, stdin));
        assertThat(response.getStatus()).isEqualTo(CodeExecutionResponse.Status.SUCCESS);
        assertThat(response.getOutput()).contains(expected);
    }

    @ParameterizedTest(name = "compile error {0}")
    @MethodSource("compileErrorCases")
    void compileError(String language, String code) {
        assumeLanguage(language);
        CodeExecutionResponse response = service.execute(new CodeExecutionRequest(language, code, ""));
        assertThat(response.getStatus()).isEqualTo(CodeExecutionResponse.Status.COMPILE_ERROR);
        assertThat(response.getError()).isNotBlank();
    }

    @ParameterizedTest(name = "runtime error {0}")
    @MethodSource("runtimeErrorCases")
    void runtimeError(String language, String code) {
        assumeLanguage(language);
        CodeExecutionResponse response = service.execute(new CodeExecutionRequest(language, code, ""));
        assertThat(response.getStatus()).isEqualTo(CodeExecutionResponse.Status.RUNTIME_ERROR);
    }

    @Test
    void javaMultiFileProject() {
        assumeLanguage("java");
        CodeExecutionResponse response = service.execute(new CodeExecutionRequest(
                "java",
                List.of(
                        new com.coderplatform.model.ProjectFile("Main.java", """
                                public class Main {
                                    public static void main(String[] args) {
                                        System.out.println(Util.tag());
                                    }
                                }
                                """),
                        new com.coderplatform.model.ProjectFile("Util.java", """
                                public class Util {
                                    public static String tag() { return "ok-java-project"; }
                                }
                                """)
                ),
                "Main.java",
                ""
        ));
        assertThat(response.getStatus()).isEqualTo(CodeExecutionResponse.Status.SUCCESS);
        assertThat(response.getOutput()).contains("ok-java-project");
    }

    @Test
    void cHeaderAndSourceProject() {
        assumeLanguage("c");
        CodeExecutionResponse response = service.execute(new CodeExecutionRequest(
                "c",
                List.of(
                        new com.coderplatform.model.ProjectFile("main.c", """
                                #include <stdio.h>
                                #include "util.h"
                                int main(void) { printf("%s\\n", label()); return 0; }
                                """),
                        new com.coderplatform.model.ProjectFile("util.c", """
                                #include "util.h"
                                const char* label(void) { return "ok-c-project"; }
                                """),
                        new com.coderplatform.model.ProjectFile("util.h", "const char* label(void);")
                ),
                "main.c",
                ""
        ));
        assertThat(response.getStatus()).isEqualTo(CodeExecutionResponse.Status.SUCCESS);
        assertThat(response.getOutput()).contains("ok-c-project");
    }

    @Test
    void timeout() {
        assumeLanguage("python");
        config.setTimeout(800);
        CodeExecutionResponse response = service.execute(new CodeExecutionRequest(
                "python", "import time\ntime.sleep(8)", ""));
        assertThat(response.getStatus()).isEqualTo(CodeExecutionResponse.Status.TIMEOUT);
    }

    @Test
    void memoryIssue() {
        assumeLanguage("python");
        CodeExecutionResponse response = service.execute(new CodeExecutionRequest(
                "python", "raise MemoryError('allocation failed')", ""));
        assertThat(response.getStatus()).isEqualTo(CodeExecutionResponse.Status.MEMORY_EXCEEDED);
    }

    static Stream<Arguments> successCases() {
        return Stream.of(
                Arguments.of("java", "public class Main { public static void main(String[] args) { System.out.println(\"ok-java\"); } }", "ok-java"),
                Arguments.of("python", "print('ok-python')", "ok-python"),
                Arguments.of("javascript", "console.log('ok-js')", "ok-js"),
                Arguments.of("typescript", "const msg: string = 'ok-ts';\nconsole.log(msg);", "ok-ts"),
                Arguments.of("c", "#include <stdio.h>\nint main() { printf(\"ok-c\\n\"); return 0; }", "ok-c"),
                Arguments.of("cpp", "#include <iostream>\nint main() { std::cout << \"ok-cpp\" << std::endl; return 0; }", "ok-cpp"),
                Arguments.of("go", "package main\nimport \"fmt\"\nfunc main() { fmt.Println(\"ok-go\") }", "ok-go"),
                Arguments.of("rust", "fn main() { println!(\"ok-rust\"); }", "ok-rust"),
                Arguments.of("ruby", "puts 'ok-ruby'", "ok-ruby"),
                Arguments.of("php", "<?php echo \"ok-php\\n\";", "ok-php"),
                Arguments.of("kotlin", "fun main() { println(\"ok-kotlin\") }", "ok-kotlin"),
                Arguments.of("swift", "print(\"ok-swift\")", "ok-swift"),
                Arguments.of("perl", "print \"ok-perl\\n\";", "ok-perl"),
                Arguments.of("bash", "echo ok-bash", "ok-bash")
        );
    }

    static Stream<Arguments> stdinCases() {
        return Stream.of(
                Arguments.of("python", "print('got:' + input())", "alpha\n", "got:alpha"),
                Arguments.of("javascript", "const fs=require('fs'); const line=fs.readFileSync(0,'utf8').trim(); console.log('got:'+line);", "beta\n", "got:beta"),
                Arguments.of("ruby", "puts 'got:' + STDIN.gets.to_s.strip", "gamma\n", "got:gamma"),
                Arguments.of("php", "<?php echo 'got:'.trim(fgets(STDIN));", "delta\n", "got:delta"),
                Arguments.of("perl", "chomp(my $l = <>); print \"got:$l\\n\";", "epsilon\n", "got:epsilon"),
                Arguments.of("bash", "read line; echo \"got:$line\"", "zeta\n", "got:zeta"),
                Arguments.of("go", "package main\nimport (\"bufio\"; \"fmt\"; \"os\")\nfunc main() { r:=bufio.NewReader(os.Stdin); l,_,_:=r.ReadLine(); fmt.Printf(\"got:%s\\n\", l) }", "eta\n", "got:eta")
        );
    }

    static Stream<Arguments> compileErrorCases() {
        return Stream.of(
                Arguments.of("java", "public class Main { this is not java }"),
                Arguments.of("c", "int main( { return 0; }"),
                Arguments.of("cpp", "int main( { return 0; }"),
                Arguments.of("rust", "fn main( { }"),
                Arguments.of("kotlin", "fun main( { }"),
                Arguments.of("typescript", "const x: number = 'nope';")
        );
    }

    static Stream<Arguments> runtimeErrorCases() {
        return Stream.of(
                Arguments.of("python", "raise RuntimeError('boom')"),
                Arguments.of("javascript", "throw new Error('boom')"),
                Arguments.of("ruby", "raise 'boom'"),
                Arguments.of("php", "<?php throw new Exception('boom');"),
                Arguments.of("perl", "die 'boom';"),
                Arguments.of("bash", "exit 7"),
                Arguments.of("java", "public class Main { public static void main(String[] args) { throw new RuntimeException(\"boom\"); } }"),
                Arguments.of("go", "package main\nfunc main() { panic(\"boom\") }")
        );
    }

    private static void assumeLanguage(String language) {
        Language lang = Language.fromId(language);
        for (String command : requiredCommands(lang)) {
            assumeTrue(commandExists(command), command + " is required for " + language + " container tests");
        }
    }

    private static List<String> requiredCommands(Language language) {
        return switch (language) {
            case JAVA -> List.of("javac", "java");
            case PYTHON -> List.of("python3");
            case JAVASCRIPT -> List.of("node");
            case TYPESCRIPT -> List.of("npx", "node");
            case C -> List.of("gcc");
            case CPP -> List.of("g++");
            case GO -> List.of("go");
            case RUST -> List.of("rustc");
            case RUBY -> List.of("ruby");
            case PHP -> List.of("php");
            case KOTLIN -> List.of("kotlinc", "java");
            case SWIFT -> List.of("swift");
            case PERL -> List.of("perl");
            case BASH -> List.of("bash");
        };
    }

    private static boolean commandExists(String command) {
        try {
            Process process = new ProcessBuilder("bash", "-lc", "command -v " + command.toLowerCase(Locale.ROOT)).start();
            return process.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
