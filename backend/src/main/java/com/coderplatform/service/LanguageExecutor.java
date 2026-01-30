package com.coderplatform.service;

import com.coderplatform.model.Language;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

@Component
public class LanguageExecutor {

    private static final Logger logger = LoggerFactory.getLogger(LanguageExecutor.class);

    public List<String> getCompileCommand(Language language, File sourceFile, File workDir) {
        List<String> command = new ArrayList<>();
        String fileName = sourceFile.getName();
        String baseName = fileName.substring(0, fileName.lastIndexOf('.'));

        switch (language) {
            case JAVA:
                command.add("javac");
                command.add(fileName);
                break;

            case TYPESCRIPT:
                command.add("npx");
                command.add("tsc");
                command.add("--outDir");
                command.add(".");
                command.add(fileName);
                break;

            case C:
                command.add("gcc");
                command.add("-o");
                command.add(baseName);
                command.add(fileName);
                break;

            case CPP:
                command.add("g++");
                command.add("-o");
                command.add(baseName);
                command.add(fileName);
                break;

            case RUST:
                command.add("rustc");
                command.add("-o");
                command.add(baseName);
                command.add(fileName);
                break;

            case KOTLIN:
                command.add("kotlinc");
                command.add(fileName);
                command.add("-include-runtime");
                command.add("-d");
                command.add(baseName + ".jar");
                break;

            default:
                break;
        }

        logger.debug("Compile command for {}: {}", language, command);
        return command;
    }

    public List<String> getRunCommand(Language language, File sourceFile, File workDir, long memoryLimitBytes) {
        List<String> command = new ArrayList<>();
        String fileName = sourceFile.getName();
        String baseName = fileName.substring(0, fileName.lastIndexOf('.'));
        long memoryLimitKB = memoryLimitBytes / 1024;

        // Add memory limit wrapper for Unix systems
        boolean isUnix = !System.getProperty("os.name").toLowerCase().contains("windows");

        switch (language) {
            case JAVA:
                command.add("java");
                command.add("-Xmx" + memoryLimitKB + "k");
                command.add(baseName);
                break;

            case PYTHON:
                command.add("python3");
                command.add(fileName);
                break;

            case JAVASCRIPT:
                command.add("node");
                command.add("--max-old-space-size=" + (memoryLimitKB / 1024));
                command.add(fileName);
                break;

            case TYPESCRIPT:
                command.add("node");
                command.add("--max-old-space-size=" + (memoryLimitKB / 1024));
                command.add(baseName + ".js");
                break;

            case C:
            case CPP:
            case RUST:
                command.add("./" + baseName);
                break;

            case GO:
                command.add("go");
                command.add("run");
                command.add(fileName);
                break;

            case RUBY:
                command.add("ruby");
                command.add(fileName);
                break;

            case PHP:
                command.add("php");
                command.add(fileName);
                break;

            case KOTLIN:
                command.add("java");
                command.add("-Xmx" + memoryLimitKB + "k");
                command.add("-jar");
                command.add(baseName + ".jar");
                break;

            case SWIFT:
                command.add("swift");
                command.add(fileName);
                break;

            case PERL:
                command.add("perl");
                command.add(fileName);
                break;

            case BASH:
                command.add("bash");
                command.add(fileName);
                break;

            default:
                throw new IllegalArgumentException("Unsupported language: " + language);
        }

        logger.debug("Run command for {}: {}", language, command);
        return command;
    }

    public String getDefaultFileName(Language language) {
        switch (language) {
            case JAVA:
                return "Main.java";
            case PYTHON:
                return "main.py";
            case JAVASCRIPT:
                return "main.js";
            case TYPESCRIPT:
                return "main.ts";
            case C:
                return "main.c";
            case CPP:
                return "main.cpp";
            case GO:
                return "main.go";
            case RUST:
                return "main.rs";
            case RUBY:
                return "main.rb";
            case PHP:
                return "main.php";
            case KOTLIN:
                return "Main.kt";
            case SWIFT:
                return "main.swift";
            case PERL:
                return "main.pl";
            case BASH:
                return "main.sh";
            default:
                return "code" + language.getExtension();
        }
    }
}
