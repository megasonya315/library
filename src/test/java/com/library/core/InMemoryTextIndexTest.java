package com.library.core;

import com.library.api.IndexConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryTextIndexTest {
    private InMemoryTextIndex index;
    @TempDir Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        index = new InMemoryTextIndex(
                IndexConfig.builder().debounceMs(0).maxFileSizeBytes(1024 * 1024).build(),
                new DefaultTextTokenizer()
        );
    }

    @AfterEach
    void tearDown() throws Exception {
        index.close();
    }

    @Test
    void addAndSearch() throws Exception {
        Path file = tempDir.resolve("test.txt");
        Files.writeString(file, "hello java world");
        index.addPath(file);

        awaitCondition(() -> index.search("hello").stream().anyMatch(p -> isSameFile(p, file)), 3000, "Файл не появился в поиске");

        assertEquals(1, index.getIndexedFilesCount());
        assertEquals(3, index.getUniqueWordsCount());
    }

    @Test
    void removeFile() throws Exception {
        Path file = tempDir.resolve("del.txt");
        Files.writeString(file, "delete me");
        index.addPath(file);
        awaitCondition(() -> index.search("delete").stream().anyMatch(p -> isSameFile(p, file)), 3000, "Файл не проиндексирован");

        index.removePath(file);
        awaitCondition(() -> index.search("delete").isEmpty(), 3000, "Файл не удалён из индекса");

        assertEquals(0, index.getIndexedFilesCount());
    }

    @Test
    void idempotentAdd() throws Exception {
        Path file = tempDir.resolve("dup.txt");
        Files.writeString(file, "unique");

        index.addPath(file);
        index.addPath(file); // Повторная отправка задачи в пул

        awaitCondition(() -> index.search("unique").stream().anyMatch(p -> isSameFile(p, file)), 3000, "Файл не найден в поиске");
        Thread.sleep(200);

        assertEquals(1, index.getIndexedFilesCount(), "В индексе должен остаться ровно 1 файл");
        assertEquals(1, index.getUniqueWordsCount(), "Должно быть ровно 1 уникальное слово");
    }

    @Test
    void searchNonExistentWord() {
        assertTrue(index.search("nonexistent").isEmpty());
    }

    @Test
    void invalidSearchQuery() {
        assertThrows(IllegalArgumentException.class, () -> index.search(""));
        assertThrows(IllegalArgumentException.class, () -> index.search("   "));
        assertThrows(IllegalArgumentException.class, () -> index.search(null));
    }

    @Test
    void skipBinaryFile() throws Exception {
        Path bin = tempDir.resolve("data.bin");
        Files.write(bin, new byte[]{0x00, 0x01, (byte) 0xFF, (byte) 0x80, (byte) 0x81});

        assertDoesNotThrow(() -> index.addPath(bin));
        awaitCondition(() -> index.getIndexedFilesCount() == 0, 2000, "Бинарный файл попал в индекс");
    }

    private boolean isSameFile(Path p1, Path p2) {
        try { return Files.isSameFile(p1, p2); } catch (IOException e) { return false; }
    }

    private void awaitCondition(BooleanSupplier condition, long timeoutMs, String message) throws InterruptedException {
        long start = System.currentTimeMillis();
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() - start > timeoutMs) {
                fail(message + " (timeout: " + timeoutMs + " ms)");
            }
            Thread.sleep(50);
        }
    }
}