package com.coderplatform.service;

import com.coderplatform.exception.InvalidProjectException;
import com.coderplatform.model.Language;
import com.coderplatform.model.ProjectFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ProjectSources {

    public static final int MAX_FILES = 32;
    public static final int MAX_PATH_LENGTH = 180;
    public static final int MAX_PATH_DEPTH = 8;
    public static final int MAX_FILE_BYTES = 256 * 1024;
    public static final int MAX_PROJECT_BYTES = 512 * 1024;

    private static final Pattern JAVA_PUBLIC_CLASS = Pattern.compile("public\\s+class\\s+(\\w+)");
    private static final Pattern JAVA_PACKAGE = Pattern.compile("(?m)^\\s*package\\s+([A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z_][A-Za-z0-9_]*)*)\\s*;");
    private static final Pattern SEGMENT = Pattern.compile("[A-Za-z0-9._][A-Za-z0-9._-]*");

    private final String language;
    private final List<ProjectFile> files;
    private final String entrypoint;

    private ProjectSources(String language, List<ProjectFile> files, String entrypoint) {
        this.language = language;
        this.files = List.copyOf(files);
        this.entrypoint = entrypoint;
    }

    public String getLanguage() {
        return language;
    }

    public List<ProjectFile> getFiles() {
        return files;
    }

    public String getEntrypoint() {
        return entrypoint;
    }

    public String entrypointContent() {
        for (ProjectFile file : files) {
            if (entrypoint.equals(file.getPath())) {
                return file.getContent() == null ? "" : file.getContent();
            }
        }
        return "";
    }

    public static ProjectSources resolve(
            String languageId,
            String code,
            List<ProjectFile> files,
            String entrypoint
    ) {
        Language language = Language.fromId(languageId);
        if (files == null || files.isEmpty()) {
            return fromSingleSource(language, code);
        }
        return fromFiles(language, files, entrypoint);
    }

    public static String defaultFileName(Language language) {
        return switch (language) {
            case JAVA -> "Main.java";
            case PYTHON -> "main.py";
            case JAVASCRIPT -> "main.js";
            case TYPESCRIPT -> "main.ts";
            case C -> "main.c";
            case CPP -> "main.cpp";
            case GO -> "main.go";
            case RUST -> "main.rs";
            case RUBY -> "main.rb";
            case PHP -> "main.php";
            case KOTLIN -> "Main.kt";
            case SWIFT -> "main.swift";
            case PERL -> "main.pl";
            case BASH -> "main.sh";
        };
    }

    public static String normalizePath(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            throw new InvalidProjectException("File path is required");
        }
        String path = rawPath.trim().replace('\\', '/');
        if (path.indexOf('\0') >= 0) {
            throw new InvalidProjectException("File path contains invalid characters");
        }
        for (int i = 0; i < path.length(); i++) {
            if (Character.isISOControl(path.charAt(i))) {
                throw new InvalidProjectException("File path contains invalid characters");
            }
        }
        if (path.startsWith("/") || path.startsWith("~") || path.matches("^[A-Za-z]:.*")) {
            throw new InvalidProjectException("Absolute file paths are not allowed");
        }
        while (path.startsWith("./")) {
            path = path.substring(2);
        }
        if (path.endsWith("/")) {
            throw new InvalidProjectException("File path must name a file, not a directory");
        }
        String[] parts = path.split("/");
        if (parts.length == 0 || parts.length > MAX_PATH_DEPTH) {
            throw new InvalidProjectException("File path is too deep");
        }
        List<String> segments = new ArrayList<>();
        for (String part : parts) {
            if (part.isEmpty() || ".".equals(part)) {
                continue;
            }
            if ("..".equals(part)) {
                throw new InvalidProjectException("File path cannot contain '..'");
            }
            if (!SEGMENT.matcher(part).matches()) {
                throw new InvalidProjectException("File path contains invalid characters: " + rawPath);
            }
            segments.add(part);
        }
        if (segments.isEmpty()) {
            throw new InvalidProjectException("File path is required");
        }
        String normalized = String.join("/", segments);
        if (normalized.length() > MAX_PATH_LENGTH) {
            throw new InvalidProjectException("File path exceeds " + MAX_PATH_LENGTH + " characters");
        }
        return normalized;
    }

    public static String javaMainClass(String entrypointPath, String source) {
        String fileName = entrypointPath.substring(entrypointPath.lastIndexOf('/') + 1);
        String className = fileName.endsWith(".java")
                ? fileName.substring(0, fileName.length() - 5)
                : fileName;
        Matcher publicClass = JAVA_PUBLIC_CLASS.matcher(source == null ? "" : source);
        if (publicClass.find()) {
            className = publicClass.group(1);
        }
        Matcher pkg = JAVA_PACKAGE.matcher(source == null ? "" : source);
        if (pkg.find()) {
            return pkg.group(1) + "." + className;
        }
        return className;
    }

    public ProjectLayout materialize(Path workDir) throws IOException {
        Path root = workDir.toAbsolutePath().normalize();
        Files.createDirectories(root);
        for (ProjectFile file : files) {
            Path dest = root.resolve(file.getPath()).normalize();
            if (!dest.startsWith(root)) {
                throw new InvalidProjectException("File path escapes the project directory: " + file.getPath());
            }
            Path parent = dest.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(dest, file.getContent() == null ? "" : file.getContent(), StandardCharsets.UTF_8);
        }
        if (shouldWriteGoMod()) {
            Path goMod = root.resolve("go.mod");
            if (!Files.exists(goMod)) {
                Files.writeString(goMod, "module playground\n\ngo 1.21\n", StandardCharsets.UTF_8);
            }
        }
        return new ProjectLayout(root.toFile(), entrypoint, files.stream().map(ProjectFile::getPath).toList());
    }

    private boolean shouldWriteGoMod() {
        if (!"go".equals(language)) {
            return false;
        }
        long goFiles = files.stream()
                .map(file -> file.getPath().toLowerCase(Locale.ROOT))
                .filter(path -> path.endsWith(".go"))
                .count();
        boolean hasMod = files.stream().anyMatch(file -> "go.mod".equals(file.getPath()));
        return goFiles > 1 && !hasMod;
    }

    private static ProjectSources fromSingleSource(Language language, String code) {
        if (code == null || code.isBlank()) {
            throw new InvalidProjectException("Code is required");
        }
        validateByteSize(code, MAX_FILE_BYTES, "Code");
        String fileName = defaultFileName(language);
        if (language == Language.JAVA) {
            fileName = javaMainClass(fileName, code);
            if (fileName.contains(".")) {
                fileName = fileName.substring(fileName.lastIndexOf('.') + 1);
            }
            fileName = fileName + ".java";
        }
        return new ProjectSources(language.getId(), List.of(new ProjectFile(fileName, code)), fileName);
    }

    private static ProjectSources fromFiles(Language language, List<ProjectFile> files, String entrypoint) {
        if (files.size() > MAX_FILES) {
            throw new InvalidProjectException("Projects are limited to " + MAX_FILES + " files");
        }
        Map<String, ProjectFile> byPath = new LinkedHashMap<>();
        int totalBytes = 0;
        for (ProjectFile file : files) {
            if (file == null) {
                throw new InvalidProjectException("File path is required");
            }
            String path = normalizePath(file.getPath());
            String content = file.getContent() == null ? "" : file.getContent();
            validateByteSize(content, MAX_FILE_BYTES, path);
            totalBytes += utf8Length(content);
            if (byPath.containsKey(path)) {
                throw new InvalidProjectException("Duplicate file path: " + path);
            }
            byPath.put(path, new ProjectFile(path, content));
        }
        if (byPath.isEmpty()) {
            throw new InvalidProjectException("Code is required");
        }
        if (totalBytes > MAX_PROJECT_BYTES) {
            throw new InvalidProjectException("Project exceeds maximum size of 512KB");
        }
        String resolvedEntry = entrypoint == null || entrypoint.isBlank()
                ? inferEntrypoint(language, byPath)
                : normalizePath(entrypoint);
        if (!byPath.containsKey(resolvedEntry)) {
            throw new InvalidProjectException("Entrypoint does not exist: " + resolvedEntry);
        }
        return new ProjectSources(language.getId(), new ArrayList<>(byPath.values()), resolvedEntry);
    }

    private static String inferEntrypoint(Language language, Map<String, ProjectFile> files) {
        String defaultName = defaultFileName(language);
        if (files.containsKey(defaultName)) {
            return defaultName;
        }
        String extension = language.getExtension();
        for (String path : files.keySet()) {
            if (path.toLowerCase(Locale.ROOT).endsWith(extension)) {
                return path;
            }
        }
        return files.keySet().iterator().next();
    }

    private static void validateByteSize(String value, int maxBytes, String label) {
        if (utf8Length(value) > maxBytes) {
            throw new InvalidProjectException(label + " exceeds maximum size of 256KB");
        }
    }

    private static int utf8Length(String value) {
        return value.getBytes(StandardCharsets.UTF_8).length;
    }
}
