package com.coderplatform.model;

public class ProjectFile {

    private String path;
    private String content = "";

    public ProjectFile() {
    }

    public ProjectFile(String path, String content) {
        this.path = path;
        this.content = content == null ? "" : content;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content == null ? "" : content;
    }
}
