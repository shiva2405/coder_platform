package com.coderplatform.model;

public class LanguageInfo {

    private String id;
    private String name;
    private String extension;
    private String sampleCode;

    public LanguageInfo() {
    }

    public LanguageInfo(String id, String name, String extension, String sampleCode) {
        this.id = id;
        this.name = name;
        this.extension = extension;
        this.sampleCode = sampleCode;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getExtension() {
        return extension;
    }

    public void setExtension(String extension) {
        this.extension = extension;
    }

    public String getSampleCode() {
        return sampleCode;
    }

    public void setSampleCode(String sampleCode) {
        this.sampleCode = sampleCode;
    }
}
