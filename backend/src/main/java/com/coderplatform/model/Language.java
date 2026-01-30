package com.coderplatform.model;

public enum Language {
    JAVA("java", ".java", "Java", true, "javac", "java"),
    PYTHON("python", ".py", "Python", false, null, "python3"),
    JAVASCRIPT("javascript", ".js", "JavaScript", false, null, "node"),
    TYPESCRIPT("typescript", ".ts", "TypeScript", true, "npx tsc", "node"),
    C("c", ".c", "C", true, "gcc", null),
    CPP("cpp", ".cpp", "C++", true, "g++", null),
    GO("go", ".go", "Go", false, null, "go run"),
    RUST("rust", ".rs", "Rust", true, "rustc", null),
    RUBY("ruby", ".rb", "Ruby", false, null, "ruby"),
    PHP("php", ".php", "PHP", false, null, "php"),
    KOTLIN("kotlin", ".kt", "Kotlin", true, "kotlinc", "kotlin"),
    SWIFT("swift", ".swift", "Swift", false, null, "swift"),
    PERL("perl", ".pl", "Perl", false, null, "perl"),
    BASH("bash", ".sh", "Bash", false, null, "bash");

    private final String id;
    private final String extension;
    private final String displayName;
    private final boolean requiresCompilation;
    private final String compileCommand;
    private final String runCommand;

    Language(String id, String extension, String displayName, boolean requiresCompilation, 
             String compileCommand, String runCommand) {
        this.id = id;
        this.extension = extension;
        this.displayName = displayName;
        this.requiresCompilation = requiresCompilation;
        this.compileCommand = compileCommand;
        this.runCommand = runCommand;
    }

    public String getId() {
        return id;
    }

    public String getExtension() {
        return extension;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isRequiresCompilation() {
        return requiresCompilation;
    }

    public String getCompileCommand() {
        return compileCommand;
    }

    public String getRunCommand() {
        return runCommand;
    }

    public static Language fromId(String id) {
        for (Language lang : values()) {
            if (lang.id.equalsIgnoreCase(id)) {
                return lang;
            }
        }
        throw new IllegalArgumentException("Unknown language: " + id);
    }
}
