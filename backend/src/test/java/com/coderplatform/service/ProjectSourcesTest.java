package com.coderplatform.service;

import com.coderplatform.exception.InvalidProjectException;
import com.coderplatform.model.ProjectFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProjectSourcesTest {

    @Test
    void singleCodeBecomesDefaultFile() {
        ProjectSources project = ProjectSources.resolve("python", "print(1)", null, null);

        assertThat(project.getEntrypoint()).isEqualTo("main.py");
        assertThat(project.getFiles()).hasSize(1);
        assertThat(project.getFiles().get(0).getPath()).isEqualTo("main.py");
        assertThat(project.entrypointContent()).isEqualTo("print(1)");
    }

    @Test
    void javaSingleFileUsesPublicClassName() {
        ProjectSources project = ProjectSources.resolve(
                "java",
                "public class Greeter { public static void main(String[] args) {} }",
                null,
                null
        );

        assertThat(project.getEntrypoint()).isEqualTo("Greeter.java");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "../etc/passwd",
            "foo/../../secret",
            "/etc/passwd",
            "C:/Windows/system.ini",
            "foo\\..\\bar",
            "~/.ssh/id_rsa",
            "foo/\0bar"
    })
    void rejectsUnsafePaths(String path) {
        assertThatThrownBy(() -> ProjectSources.resolve(
                "c",
                null,
                List.of(new ProjectFile(path, "int x;")),
                path
        )).isInstanceOf(InvalidProjectException.class);
    }

    @Test
    void rejectsDuplicatePathsAfterNormalization() {
        assertThatThrownBy(() -> ProjectSources.resolve(
                "python",
                null,
                List.of(new ProjectFile("src/app.py", "print(1)"), new ProjectFile("./src/app.py", "print(2)")),
                "src/app.py"
        )).isInstanceOf(InvalidProjectException.class)
                .hasMessageContaining("Duplicate file path");
    }

    @Test
    void rejectsMissingEntrypoint() {
        assertThatThrownBy(() -> ProjectSources.resolve(
                "python",
                null,
                List.of(new ProjectFile("main.py", "print(1)")),
                "other.py"
        )).isInstanceOf(InvalidProjectException.class)
                .hasMessageContaining("Entrypoint does not exist");
    }

    @Test
    void materializeWritesNestedFilesInsideWorkDir() throws Exception {
        Path workDir = Files.createTempDirectory("project-sources-");
        ProjectSources project = ProjectSources.resolve(
                "c",
                null,
                List.of(
                        new ProjectFile("src/main.c", "int main() { return 0; }"),
                        new ProjectFile("include/util.h", "#pragma once")
                ),
                "src/main.c"
        );

        ProjectLayout layout = project.materialize(workDir);

        assertThat(layout.entrypoint()).isEqualTo("src/main.c");
        assertThat(workDir.resolve("src/main.c")).exists();
        assertThat(workDir.resolve("include/util.h")).exists();
        assertThat(workDir.resolve("src/main.c").toRealPath().startsWith(workDir.toRealPath())).isTrue();
    }

    @Test
    void javaMainClassIncludesPackage() {
        assertThat(ProjectSources.javaMainClass(
                "com/example/App.java",
                "package com.example;\npublic class App {}"
        )).isEqualTo("com.example.App");
    }
}
