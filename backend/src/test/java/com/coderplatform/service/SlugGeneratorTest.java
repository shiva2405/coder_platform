package com.coderplatform.service;

import com.coderplatform.config.SnippetConfig;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SlugGeneratorTest {

    @Test
    void generatesUrlSafeSlugsOfConfiguredLength() {
        SnippetConfig config = new SnippetConfig();
        config.setSlugLength(8);
        SlugGenerator generator = new SlugGenerator(config);

        String slug = generator.generate();

        assertThat(slug).hasSize(8).matches("[A-Za-z0-9]+");
    }

    @Test
    void generatesDistinctSlugs() {
        SlugGenerator generator = new SlugGenerator(new SnippetConfig());
        Set<String> slugs = new HashSet<>();

        for (int i = 0; i < 200; i++) {
            slugs.add(generator.generate());
        }

        assertThat(slugs.size()).isGreaterThan(190);
    }
}
