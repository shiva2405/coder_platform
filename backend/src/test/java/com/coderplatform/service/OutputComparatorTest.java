package com.coderplatform.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OutputComparatorTest {

    @Test
    void ignoresTrailingNewlinesAndWhitespace() {
        assertThat(OutputComparator.matches("3\n", "3")).isTrue();
        assertThat(OutputComparator.matches("3  \n\n", "3")).isTrue();
        assertThat(OutputComparator.matches("hello  \nworld  \n", "hello\nworld")).isTrue();
    }

    @Test
    void treatsCrlfAsLf() {
        assertThat(OutputComparator.matches("1\r\n2\r\n", "1\n2")).isTrue();
    }

    @Test
    void detectsDifferentValues() {
        assertThat(OutputComparator.matches("3", "4")).isFalse();
        assertThat(OutputComparator.matches("YES", "NO")).isFalse();
    }

    @Test
    void emptyOutputsMatch() {
        assertThat(OutputComparator.matches("", null)).isTrue();
        assertThat(OutputComparator.matches("\n", "")).isTrue();
    }
}
