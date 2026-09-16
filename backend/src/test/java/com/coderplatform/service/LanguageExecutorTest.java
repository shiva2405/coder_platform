package com.coderplatform.service;

import com.coderplatform.model.Language;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.File;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LanguageExecutorTest {

    private static final File WORK_DIR = new File("/tmp/coder-work");
    private static final long MEMORY_128_MB = 128L * 1024 * 1024;
    private static final long MEMORY_1_MB = 1024 * 1024;

    private final LanguageExecutor executor = new LanguageExecutor();

    @ParameterizedTest
    @MethodSource("compileCommands")
    void compileCommandMatchesLanguageToolchain(Language language, String fileName, List<String> expected) {
        assertThat(executor.getCompileCommand(language, new File(WORK_DIR, fileName), WORK_DIR))
                .containsExactlyElementsOf(expected);
    }

    static Stream<Arguments> compileCommands() {
        return Stream.of(
                Arguments.of(Language.JAVA, "Main.java", List.of("javac", "-d", ".", "Main.java")),
                Arguments.of(Language.TYPESCRIPT, "main.ts", List.of("npx", "tsc", "--outDir", ".", "main.ts")),
                Arguments.of(Language.C, "main.c", List.of("gcc", "-o", "main", "main.c")),
                Arguments.of(Language.CPP, "main.cpp", List.of("g++", "-o", "main", "main.cpp")),
                Arguments.of(Language.RUST, "main.rs", List.of("rustc", "-o", "main", "main.rs")),
                Arguments.of(Language.KOTLIN, "Main.kt", List.of("kotlinc", "Main.kt", "-include-runtime", "-d", "Main.jar"))
        );
    }

    @ParameterizedTest
    @EnumSource(value = Language.class, names = {
            "PYTHON", "JAVASCRIPT", "GO", "RUBY", "PHP", "SWIFT", "PERL", "BASH"
    })
    void interpretedLanguagesHaveNoCompileCommand(Language language) {
        File source = new File(WORK_DIR, executor.getDefaultFileName(language));
        assertThat(executor.getCompileCommand(language, source, WORK_DIR)).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("runCommands")
    void runCommandMatchesLanguageRuntime(Language language, String fileName, long memoryBytes, List<String> expected) {
        assertThat(executor.getRunCommand(language, new File(WORK_DIR, fileName), WORK_DIR, memoryBytes))
                .containsExactlyElementsOf(expected);
    }

    static Stream<Arguments> runCommands() {
        return Stream.of(
                Arguments.of(Language.JAVA, "Main.java", MEMORY_128_MB, List.of("java", "-Xmx128m", "-Xms4m", "-cp", ".", "Main")),
                Arguments.of(Language.PYTHON, "main.py", MEMORY_128_MB, List.of("python3", "-u", "main.py")),
                Arguments.of(Language.JAVASCRIPT, "main.js", MEMORY_128_MB, List.of("node", "--max-old-space-size=128", "main.js")),
                Arguments.of(Language.TYPESCRIPT, "main.ts", MEMORY_128_MB, List.of("node", "--max-old-space-size=128", "main.js")),
                Arguments.of(Language.C, "main.c", MEMORY_128_MB, List.of("./main")),
                Arguments.of(Language.CPP, "main.cpp", MEMORY_128_MB, List.of("./main")),
                Arguments.of(Language.RUST, "main.rs", MEMORY_128_MB, List.of("./main")),
                Arguments.of(Language.GO, "main.go", MEMORY_128_MB, List.of("go", "run", "main.go")),
                Arguments.of(Language.RUBY, "main.rb", MEMORY_128_MB, List.of("ruby", "main.rb")),
                Arguments.of(Language.PHP, "main.php", MEMORY_128_MB, List.of("php", "main.php")),
                Arguments.of(Language.KOTLIN, "Main.kt", MEMORY_128_MB, List.of("java", "-Xmx128m", "-Xms4m", "-jar", "Main.jar")),
                Arguments.of(Language.SWIFT, "main.swift", MEMORY_128_MB, List.of("swift", "main.swift")),
                Arguments.of(Language.PERL, "main.pl", MEMORY_128_MB, List.of("perl", "main.pl")),
                Arguments.of(Language.BASH, "main.sh", MEMORY_128_MB, List.of("bash", "main.sh"))
        );
    }

    @Test
    void javaAndKotlinEnforceMinimumHeapWhenMemoryLimitIsTiny() {
        List<String> java = executor.getRunCommand(Language.JAVA, new File(WORK_DIR, "Main.java"), WORK_DIR, MEMORY_1_MB);
        List<String> kotlin = executor.getRunCommand(Language.KOTLIN, new File(WORK_DIR, "Main.kt"), WORK_DIR, MEMORY_1_MB);

        assertThat(java).containsExactly("java", "-Xmx8m", "-Xms4m", "-cp", ".", "Main");
        assertThat(kotlin).containsExactly("java", "-Xmx8m", "-Xms4m", "-jar", "Main.jar");
    }

    @Test
    void nodeEnforcesMinimumOldSpaceWhenMemoryLimitIsTiny() {
        List<String> javascript = executor.getRunCommand(Language.JAVASCRIPT, new File(WORK_DIR, "main.js"), WORK_DIR, MEMORY_1_MB);
        List<String> typescript = executor.getRunCommand(Language.TYPESCRIPT, new File(WORK_DIR, "main.ts"), WORK_DIR, MEMORY_1_MB);

        assertThat(javascript).containsExactly("node", "--max-old-space-size=4", "main.js");
        assertThat(typescript).containsExactly("node", "--max-old-space-size=4", "main.js");
    }

    @ParameterizedTest
    @MethodSource("defaultFileNames")
    void defaultFileNameMatchesLanguage(Language language, String expected) {
        assertThat(executor.getDefaultFileName(language)).isEqualTo(expected);
    }

    static Stream<Arguments> defaultFileNames() {
        return Stream.of(
                Arguments.of(Language.JAVA, "Main.java"),
                Arguments.of(Language.PYTHON, "main.py"),
                Arguments.of(Language.JAVASCRIPT, "main.js"),
                Arguments.of(Language.TYPESCRIPT, "main.ts"),
                Arguments.of(Language.C, "main.c"),
                Arguments.of(Language.CPP, "main.cpp"),
                Arguments.of(Language.GO, "main.go"),
                Arguments.of(Language.RUST, "main.rs"),
                Arguments.of(Language.RUBY, "main.rb"),
                Arguments.of(Language.PHP, "main.php"),
                Arguments.of(Language.KOTLIN, "Main.kt"),
                Arguments.of(Language.SWIFT, "main.swift"),
                Arguments.of(Language.PERL, "main.pl"),
                Arguments.of(Language.BASH, "main.sh")
        );
    }

    @Test
    void unknownLanguageIdIsRejected() {
        assertThatThrownBy(() -> Language.fromId("cobol"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown language: cobol");
    }

    @Test
    void javaCompilesEverySourceFile() {
        ProjectLayout layout = new ProjectLayout(
                WORK_DIR,
                "Main.java",
                List.of("Main.java", "util/Helper.java")
        );

        assertThat(executor.getCompileCommand(Language.JAVA, layout, "public class Main {}"))
                .containsExactly("javac", "-d", ".", "Main.java", "util/Helper.java");
        assertThat(executor.getRunCommand(Language.JAVA, layout, "public class Main {}", MEMORY_128_MB))
                .containsExactly("java", "-Xmx128m", "-Xms4m", "-cp", ".", "Main");
    }

    @Test
    void cCompilesSourcesTogetherAndLeavesHeadersOut() {
        ProjectLayout layout = new ProjectLayout(
                WORK_DIR,
                "main.c",
                List.of("main.c", "util.c", "util.h")
        );

        assertThat(executor.getCompileCommand(Language.C, layout, ""))
                .containsExactly("gcc", "-I.", "-o", "program", "main.c", "util.c");
        assertThat(executor.getRunCommand(Language.C, layout, "", MEMORY_128_MB))
                .containsExactly("./program");
    }

    @Test
    void goRunsTheModuleWhenMultipleSourcesExist() {
        ProjectLayout layout = new ProjectLayout(WORK_DIR, "main.go", List.of("main.go", "helper.go"));

        assertThat(executor.getRunCommand(Language.GO, layout, "", MEMORY_128_MB))
                .containsExactly("go", "run", ".");
    }
}
