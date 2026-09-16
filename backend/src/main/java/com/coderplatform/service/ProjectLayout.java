package com.coderplatform.service;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ProjectLayout {

    private final File workDir;
    private final String entrypoint;
    private final List<String> paths;

    public ProjectLayout(File workDir, String entrypoint, List<String> paths) {
        this.workDir = workDir;
        this.entrypoint = entrypoint;
        this.paths = List.copyOf(paths);
    }

    public File workDir() {
        return workDir;
    }

    public String entrypoint() {
        return entrypoint;
    }

    public File entrypointFile() {
        return new File(workDir, entrypoint.replace('/', File.separatorChar));
    }

    public List<String> paths() {
        return paths;
    }

    public boolean isSingleFile() {
        return paths.size() == 1;
    }

    public List<File> filesWithExtensions(String... extensions) {
        List<File> matches = new ArrayList<>();
        for (String path : paths) {
            String lower = path.toLowerCase(Locale.ROOT);
            for (String extension : extensions) {
                if (lower.endsWith(extension.toLowerCase(Locale.ROOT))) {
                    matches.add(new File(workDir, path.replace('/', File.separatorChar)));
                    break;
                }
            }
        }
        return matches;
    }

    public Path entrypointRelative() {
        return Path.of(entrypoint);
    }

    public String entrypointBaseName() {
        String name = entrypointFile().getName();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    public static ProjectLayout single(File sourceFile, File workDir) {
        return new ProjectLayout(workDir, sourceFile.getName(), List.of(sourceFile.getName()));
    }
}
