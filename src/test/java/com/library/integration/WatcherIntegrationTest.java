package com.library.integration;

import com.library.TextIndexFactory;
import com.library.api.IndexConfig;
import com.library.api.TextIndex;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class WatcherIntegrationTest {
    private TextIndex index;
    @TempDir Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        index = TextIndexFactory.createTextIndex(IndexConfig.builder().debounceMs(100).build());
    }

    @AfterEach
    void tearDown() throws Exception {
        index.close();
    }

    @Test
    void autoIndexOnFileCreateAndModify() throws Exception {
        index.addPath(tempDir);

        Path file = tempDir.resolve("test.txt");
        Files.writeString(file, "hello world");
        waitForIndexUpdate();

        boolean found = index.search("hello").stream()
                .anyMatch(p -> {
                    try { return Files.isSameFile(p, file); } catch (IOException e) { return false; }
                });
        assertTrue(found, "Файл должен появиться после создания");

        // 2. Изменение содержимого
        Files.writeString(file, "hello java");
        waitForIndexUpdate();

        assertTrue(index.search("hello").stream().anyMatch(p -> {
            try { return Files.isSameFile(p, file); } catch (IOException e) { return false; }
        }));
        assertTrue(index.search("java").stream().anyMatch(p -> {
            try { return Files.isSameFile(p, file); } catch (IOException e) { return false; }
        }));
        assertFalse(index.search("world").stream().anyMatch(p -> {
            try { return Files.isSameFile(p, file); } catch (IOException e) { return false; }
        }), "Слово 'world' должно исчезнуть после перезаписи");
    }

    @Test
    void autoRemoveOnFileDelete() throws Exception {
        index.addPath(tempDir);
        Path file = tempDir.resolve("del.txt");
        Files.writeString(file, "delete me");
        waitForIndexUpdate();

        boolean found = index.search("delete").stream()
                .anyMatch(p -> {
                    try { return Files.isSameFile(p, file); } catch (IOException e) { return false; }
                });
        assertTrue(found, "Файл должен быть в индексе перед удалением");

        Files.delete(file);
        waitForIndexUpdate();

        assertTrue(index.search("delete").isEmpty(), "Файл должен исчезнуть из индекса после удаления");
    }

    private void waitForIndexUpdate() throws InterruptedException {
        Thread.sleep(1000);
    }
}