package com.library.core;

import com.library.api.IndexConfig;
import com.library.api.TextIndex;
import com.library.api.TextTokenizer;
import com.library.watcher.RecursiveFileWatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Stream;


public class InMemoryTextIndex implements TextIndex {

    private static final Logger log = LoggerFactory.getLogger(InMemoryTextIndex.class);

    private final ConcurrentHashMap<Path, Set<String>> fileIndex = new ConcurrentHashMap<>(); //прямой индекс для быстрого удаления
    private final ConcurrentHashMap<String, Set<Path>> wordIndex = new ConcurrentHashMap<>(); //обратный индекс для поиска за O(1)
    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();
    private final ExecutorService executor;
    private final IndexConfig config;
    private final TextTokenizer tokenizer;
    private volatile boolean closed = false;
    private final RecursiveFileWatcher watcher;

    public InMemoryTextIndex(IndexConfig config, TextTokenizer tokenizer) {
        this.config = Objects.requireNonNull(config, "Конфиг не может быть null");
        this.tokenizer = Objects.requireNonNull(tokenizer, "Токенизатор не может быть null");
        this.executor = new ThreadPoolExecutor(
                config.getMaxThreads(),
                config.getMaxThreads(),
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                new NamedThreadFactory("textindex-worker"),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );

        try {
            this.watcher = new RecursiveFileWatcher(config, this::submitIndexTask);
            this.watcher.start();
        } catch (IOException e) {
            throw new UncheckedIOException("Не удалось инициализировать watcher", e);
        }
    }

    @Override
    public void addPath(Path path) throws IOException {
        if (closed) throw new IllegalStateException("Индекс закрыт");
        Path realPath = path.toRealPath().toAbsolutePath().normalize();

        if (Files.isRegularFile(realPath)) {
            submitIndexTask(realPath);
        } else if (Files.isDirectory(realPath)) {
            try (Stream<Path> stream = Files.walk(realPath)) {
                stream.filter(Files::isRegularFile)
                        .filter(Files::isReadable)
                        .forEach(this::submitIndexTask);
            } catch (IOException e) {
                throw new IOException("Не удалось обойти каталог: " + realPath, e);
            }
            watcher.register(realPath);
        } else {
            throw new NoSuchFileException("Файл или каталог не найден: " + path);
        }
    }


    @Override
    public void removePath(Path path) throws IOException {
        if (closed) throw new IllegalStateException("Индекс закрыт");
        Path realPath = path.toRealPath().toAbsolutePath().normalize();

        rwLock.writeLock().lock();
        try {
            if (Files.isRegularFile(realPath)) {
                removeFromIndex(realPath);
            } else if (Files.isDirectory(realPath)) {
                fileIndex.keySet().stream()
                        .filter(p -> p.startsWith(realPath))
                        .toList()
                        .forEach(this::removeFromIndex);
            }
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    @Override
    public Set<Path> search(String word) {
        if (word == null || word.isBlank()) {
            throw new IllegalArgumentException("Поисковый запрос не может быть пустым или содержать только пробелы");
        }
        String normalized = word.toLowerCase();
        rwLock.readLock().lock();
        try {
            Set<Path> result = wordIndex.get(normalized);
            return result != null ? Set.copyOf(result) : Set.of();
        } finally {
            rwLock.readLock().unlock();
        }
    }

    @Override
    public long getIndexedFilesCount() {
        return fileIndex.size();
    }

    @Override
    public long getUniqueWordsCount() {
        return wordIndex.size();
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    log.warn("Executor не завершился корректно");
                }
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        watcher.close();
    }

    //Внутренние методы и классы

    private void submitIndexTask(Path file) {
        try {
            // Если файл удалён или стал директорией/недоступен, чистим индекс
            if (!Files.exists(file) || !Files.isRegularFile(file)) {
                rwLock.writeLock().lock();
                try { removeFromIndex(file); } finally { rwLock.writeLock().unlock(); }
                return;
            }

            long size = Files.size(file);
            if (size > config.getMaxFileSizeBytes()) {
                log.warn("Пропуск файла, превышающего лимит: {} ({} байт)", file, size);
                return;
            }
            executor.submit(() -> indexFile(file));
        } catch (IOException e) {
            log.debug("Нет доступа к файлу, пропускаем: {}", file, e);
        }
    }

    private void indexFile(Path file) {
        try {
            Set<String> words = new HashSet<>();
            // Stream auto-closeable. UncheckedIOException ловит MalformedInputException (бинарники)
            try (Stream<String> lines = Files.lines(file, config.getCharset())) {
                lines.flatMap(tokenizer::tokenize)
                        .forEach(words::add);
            }

            rwLock.writeLock().lock();
            try {
                // удаление старых связей
                Set<String> oldWords = fileIndex.remove(file);
                if (oldWords != null) {
                    oldWords.forEach(w -> {
                        Set<Path> files = wordIndex.get(w);
                        if (files != null && files.remove(file) && files.isEmpty()) {
                            wordIndex.remove(w);
                        }
                    });
                }

                // добавление новых
                if (!words.isEmpty()) {
                    fileIndex.put(file, words);
                    words.forEach(w ->
                            wordIndex.computeIfAbsent(w, k -> ConcurrentHashMap.newKeySet()).add(file)
                    );
                }
            } finally {
                rwLock.writeLock().unlock();
            }
        } catch (UncheckedIOException e) {
            log.warn("Пропущен нетекстовый файл: {} ({})", file, e.getCause().getMessage());
        } catch (IOException e) {
            log.error("Ошибка при индексации файла: {}", file, e);
        } catch (Exception e) {
            log.error("Непредвиденная ошибка при индексации файла {}: {}", file, e.getMessage(), e);
        }
    }

    private void removeFromIndex(Path file) {
        Set<String> words = fileIndex.remove(file);
        if (words == null) return;
        words.forEach(w -> {
            Set<Path> files = wordIndex.get(w);
            if (files != null && files.remove(file) && files.isEmpty()) {
                wordIndex.remove(w);
            }
        });
    }

    private static class NamedThreadFactory implements ThreadFactory {
        private final AtomicInteger count = new AtomicInteger(0);
        private final String prefix;
        NamedThreadFactory(String prefix) { this.prefix = prefix; }
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, prefix + "-" + count.incrementAndGet());
            t.setDaemon(true);
            return t;
        }
    }
}
