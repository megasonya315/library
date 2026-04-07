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
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

class ConcurrentAccessTest {
    private TextIndex index;
    @TempDir Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        index = TextIndexFactory.createTextIndex(IndexConfig.builder().maxThreads(4).build());
    }

    @AfterEach
    void tearDown() throws Exception {
        index.close();
    }

    @Test
    void concurrentReadWrite() throws Exception {
        int fileCount = 20;
        CountDownLatch startLatch = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(10);

        for (int i = 0; i < fileCount; i++) {
            Files.writeString(tempDir.resolve("file_" + i + ".txt"), "word_" + i);
        }

        for (int i = 0; i < 5; i++) {
            final int writerId = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    index.addPath(tempDir.resolve("file_" + writerId + ".txt"));
                } catch (Exception e) { fail("Writer failed", e); }
            });
        }

        Set<String> searchResults = Collections.synchronizedSet(new ConcurrentSkipListSet<>());
        for (int i = 0; i < 5; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int j = 0; j < 100; j++) {
                        index.search("word_" + ThreadLocalRandom.current().nextInt(fileCount));
                        searchResults.add("search_ok");
                    }
                } catch (Exception e) { fail("Reader failed", e); }
            });
        }

        startLatch.countDown();
        executor.shutdown();
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));

        assertFalse(searchResults.isEmpty());
        assertTrue(index.getIndexedFilesCount() > 0);
    }
}