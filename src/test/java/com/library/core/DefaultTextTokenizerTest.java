package com.library.core;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class DefaultTextTokenizerTest {
    private final DefaultTextTokenizer tokenizer = new DefaultTextTokenizer();

    @Test
    void emptyAndBlankStrings() {
        assertTrue(tokenizer.tokenize("").toList().isEmpty());
        assertTrue(tokenizer.tokenize("   \n\t  ").toList().isEmpty());
    }

    @Test
    void basicEnglishAndPunctuation() {
        List<String> tokens = tokenizer.tokenize("Hello, World! Hello Java.").toList();
        assertEquals(List.of("hello", "world", "hello", "java"), tokens);
    }

    @Test
    void cyrillicAndUnicode() {
        List<String> tokens = tokenizer.tokenize("Привет, мир! Тестирование  и цифры 123.").toList();
        assertEquals(List.of("привет", "мир", "тестирование", "и", "цифры"), tokens);
    }

    @Test
    void numbersAndUnderscores() {
        List<String> tokens = tokenizer.tokenize("test_123 var_name 100%").toList();
        assertEquals(List.of("test_123", "var_name"), tokens);
    }

    @Test
    void caseNormalization() {
        List<String> tokens = tokenizer.tokenize("UPPER lower MiXeD").toList();
        assertEquals(List.of("upper", "lower", "mixed"), tokens);
    }
}