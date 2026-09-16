package com.coderplatform.service;

import com.coderplatform.model.Language;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class LanguageExecutor {

    private static final Logger logger = LoggerFactory.getLogger(LanguageExecutor.class);

    public List<String> getCompileCommand(Language language, File sourceFile, File workDir) {
        return getCompileCommand(language, ProjectLayout.single(sourceFile, workDir), "");
    }

    public List<String> getCompileCommand(Language language, ProjectLayout layout, String entrypointSource) {
        List<String> command = new ArrayList<>();
        String fileName = layout.entrypointFile().getName();
        String baseName = layout.entrypointBaseName();

        switch (language) {
            case JAVA -> {
                command.add("javac");
                command.add("-d");
                command.add(".");
                List<File> javaFiles = layout.filesWithExtensions(".java");
                command.addAll(relativeNames(layout, javaFiles.isEmpty() ? List.of(layout.entrypointFile()) : javaFiles));
            }
            case TYPESCRIPT -> {
                command.add("npx");
                command.add("tsc");
                command.add("--outDir");
                command.add(".");
                List<File> tsFiles = layout.filesWithExtensions(".ts");
                if (tsFiles.size() > 1) {
                    command.add("--rootDir");
                    command.add(".");
                    command.add("--esModuleInterop");
                    command.add("--module");
                    command.add("commonjs");
                    command.add("--skipLibCheck");
                    command.addAll(relativeNames(layout, tsFiles));
                } else {
                    command.add(relativeName(layout, layout.entrypointFile()));
                }
            }
            case C -> {
                List<File> cFiles = layout.filesWithExtensions(".c");
                command.add("gcc");
                if (!layout.isSingleFile()) {
                    command.add("-I.");
                }
                command.add("-o");
                command.add(nativeBinaryName(layout, cFiles.size() > 1 ? "program" : baseName));
                command.addAll(relativeNames(layout, cFiles.isEmpty() ? List.of(layout.entrypointFile()) : cFiles));
            }
            case CPP -> {
                List<File> cppFiles = layout.filesWithExtensions(".cpp", ".cc", ".cxx");
                command.add("g++");
                if (!layout.isSingleFile()) {
                    command.add("-I.");
                }
                command.add("-o");
                command.add(nativeBinaryName(layout, cppFiles.size() > 1 ? "program" : baseName));
                command.addAll(relativeNames(layout, cppFiles.isEmpty() ? List.of(layout.entrypointFile()) : cppFiles));
            }
            case RUST -> {
                command.add("rustc");
                command.add("-o");
                command.add(layout.isSingleFile() ? baseName : "program");
                command.add(relativeName(layout, layout.entrypointFile()));
            }
            case KOTLIN -> {
                List<File> ktFiles = layout.filesWithExtensions(".kt");
                command.add("kotlinc");
                command.addAll(relativeNames(layout, ktFiles.isEmpty() ? List.of(layout.entrypointFile()) : ktFiles));
                command.add("-include-runtime");
                command.add("-d");
                command.add((ktFiles.size() > 1 ? "program" : baseName) + ".jar");
            }
            default -> {
            }
        }

        logger.debug("Compile command for {}: {}", language, command);
        return command;
    }

    public List<String> getRunCommand(Language language, File sourceFile, File workDir, long memoryLimitBytes) {
        return getRunCommand(language, ProjectLayout.single(sourceFile, workDir), "", memoryLimitBytes);
    }

    public List<String> getRunCommand(
            Language language,
            ProjectLayout layout,
            String entrypointSource,
            long memoryLimitBytes
    ) {
        List<String> command = new ArrayList<>();
        String fileName = relativeName(layout, layout.entrypointFile());
        String baseName = layout.entrypointBaseName();

        long memoryLimitMB = Math.max(memoryLimitBytes / (1024 * 1024), 1);
        long jvmMemoryMB = Math.max(memoryLimitMB, 8);
        long nodeMemoryMB = Math.max(memoryLimitMB, 4);

        switch (language) {
            case JAVA -> {
                command.add("java");
                command.add("-Xmx" + jvmMemoryMB + "m");
                command.add("-Xms" + Math.min(jvmMemoryMB, 4) + "m");
                String mainClass = ProjectSources.javaMainClass(layout.entrypoint(), entrypointSource);
                command.add("-cp");
                command.add(".");
                command.add(mainClass);
            }
            case PYTHON -> {
                command.add("python3");
                command.add("-u");
                command.add(fileName);
            }
            case JAVASCRIPT -> {
                command.add("node");
                command.add("--max-old-space-size=" + nodeMemoryMB);
                command.add(fileName);
            }
            case TYPESCRIPT -> {
                command.add("node");
                command.add("--max-old-space-size=" + nodeMemoryMB);
                command.add(replaceExtension(fileName, ".js"));
            }
            case C, CPP, RUST -> {
                List<File> sources = language == Language.RUST
                        ? List.of(layout.entrypointFile())
                        : language == Language.C
                            ? layout.filesWithExtensions(".c")
                            : layout.filesWithExtensions(".cpp", ".cc", ".cxx");
                String binary = (!layout.isSingleFile() && sources.size() > 1) || (!layout.isSingleFile() && language == Language.RUST)
                        ? "program"
                        : baseName;
                if (language == Language.RUST && !layout.isSingleFile()) {
                    binary = "program";
                }
                command.add("./" + binary);
            }
            case GO -> {
                command.add("go");
                command.add("run");
                List<File> goFiles = layout.filesWithExtensions(".go");
                if (goFiles.size() > 1) {
                    String dir = parentDir(layout.entrypoint());
                    command.add(dir.isEmpty() ? "." : "./" + dir);
                } else {
                    command.add(fileName);
                }
            }
            case RUBY -> {
                command.add("ruby");
                command.add(fileName);
            }
            case PHP -> {
                command.add("php");
                command.add(fileName);
            }
            case KOTLIN -> {
                List<File> ktFiles = layout.filesWithExtensions(".kt");
                command.add("java");
                command.add("-Xmx" + jvmMemoryMB + "m");
                command.add("-Xms" + Math.min(jvmMemoryMB, 4) + "m");
                command.add("-jar");
                command.add((ktFiles.size() > 1 ? "program" : baseName) + ".jar");
            }
            case SWIFT -> {
                command.add("swift");
                command.add(fileName);
            }
            case PERL -> {
                command.add("perl");
                command.add(fileName);
            }
            case BASH -> {
                command.add("bash");
                command.add(fileName);
            }
            default -> throw new IllegalArgumentException("Unsupported language: " + language);
        }

        logger.debug("Run command for {}: {}", language, command);
        return command;
    }

    public Map<String, String> getRunEnvironment(Language language, ProjectLayout layout) {
        Map<String, String> env = new LinkedHashMap<>();
        String workDir = layout.workDir().getAbsolutePath();
        switch (language) {
            case PYTHON -> env.put("PYTHONPATH", prependPath(System.getenv("PYTHONPATH"), workDir));
            case RUBY -> env.put("RUBYLIB", prependPath(System.getenv("RUBYLIB"), workDir));
            case JAVASCRIPT, TYPESCRIPT -> env.put("NODE_PATH", prependPath(System.getenv("NODE_PATH"), workDir));
            case GO -> {
                env.put("GO111MODULE", "on");
                env.put("GOFLAGS", "-mod=mod");
            }
            default -> {
            }
        }
        return env;
    }

    public String getDefaultFileName(Language language) {
        return ProjectSources.defaultFileName(language);
    }

    public String readEntrypointSource(ProjectLayout layout) {
        try {
            return Files.readString(layout.entrypointFile().toPath(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    private static List<String> relativeNames(ProjectLayout layout, List<File> files) {
        List<String> names = new ArrayList<>();
        for (File file : files) {
            names.add(relativeName(layout, file));
        }
        return names;
    }

    private static String relativeName(ProjectLayout layout, File file) {
        String work = layout.workDir().getAbsolutePath();
        String absolute = file.getAbsolutePath();
        if (absolute.startsWith(work)) {
            String relative = absolute.substring(work.length());
            if (relative.startsWith(File.separator)) {
                relative = relative.substring(1);
            }
            return relative.replace(File.separatorChar, '/');
        }
        return file.getName();
    }

    private static String nativeBinaryName(ProjectLayout layout, String preferred) {
        return preferred;
    }

    private static String replaceExtension(String fileName, String extension) {
        int dot = fileName.lastIndexOf('.');
        return (dot > 0 ? fileName.substring(0, dot) : fileName) + extension;
    }

    private static String parentDir(String path) {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? "" : path.substring(0, slash);
    }

    private static String prependPath(String existing, String first) {
        if (existing == null || existing.isBlank()) {
            return first;
        }
        return first + File.pathSeparator + existing;
    }
}
