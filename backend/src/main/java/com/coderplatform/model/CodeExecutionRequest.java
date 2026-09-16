package com.coderplatform.model;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;

import java.util.ArrayList;
import java.util.List;

public class CodeExecutionRequest {

    @NotBlank(message = "Language is required")
    private String language;

    private String code;

    private List<ProjectFile> files;

    private String entrypoint;

    private String stdin = "";

    public CodeExecutionRequest() {
    }

    public CodeExecutionRequest(String language, String code, String stdin) {
        this.language = language;
        this.code = code;
        this.stdin = stdin;
    }

    public CodeExecutionRequest(String language, List<ProjectFile> files, String entrypoint, String stdin) {
        this.language = language;
        this.files = files == null ? null : new ArrayList<>(files);
        this.entrypoint = entrypoint;
        this.stdin = stdin;
        if (files != null && entrypoint != null) {
            for (ProjectFile file : files) {
                if (file != null && entrypoint.equals(file.getPath())) {
                    this.code = file.getContent();
                    break;
                }
            }
        }
    }

    @AssertTrue(message = "Code is required")
    public boolean isSourcePresent() {
        if (code != null && !code.isBlank()) {
            return true;
        }
        if (files == null || files.isEmpty()) {
            return false;
        }
        return files.stream().anyMatch(file -> file != null && file.getPath() != null && !file.getPath().isBlank());
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public List<ProjectFile> getFiles() {
        return files;
    }

    public void setFiles(List<ProjectFile> files) {
        this.files = files;
    }

    public String getEntrypoint() {
        return entrypoint;
    }

    public void setEntrypoint(String entrypoint) {
        this.entrypoint = entrypoint;
    }

    public String getStdin() {
        return stdin;
    }

    public void setStdin(String stdin) {
        this.stdin = stdin;
    }
}
