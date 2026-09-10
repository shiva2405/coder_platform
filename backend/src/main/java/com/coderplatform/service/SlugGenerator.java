package com.coderplatform.service;

import com.coderplatform.config.SnippetConfig;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class SlugGenerator {

    static final char[] ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();

    private final SecureRandom random = new SecureRandom();
    private final SnippetConfig config;

    public SlugGenerator(SnippetConfig config) {
        this.config = config;
    }

    public String generate() {
        return generate(config.getSlugLength());
    }

    public String generate(int length) {
        char[] buffer = new char[length];
        for (int i = 0; i < length; i++) {
            buffer[i] = ALPHABET[random.nextInt(ALPHABET.length)];
        }
        return new String(buffer);
    }
}
