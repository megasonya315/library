package com.library.watcher;

import com.library.api.IndexConfig;

import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class RecursiveFileWatcher implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(RecursiveFileWatcher.class);

    private final WatchService watchService;
    private final ScheduledExecutorService scheduler;
    private final ConcurrentHashMap<Path, ScheduledFuture<?>> debounceMap = new ConcurrentHashMap<>();
    private final Consumer<Path> onFileChanged;
    private final int debounceMs;
    private final Thread watcherThread;
    private volatile boolean running = false;

    public RecursiveFileWatcher(IndexConfig config, Consumer<Path> onFileChanged) throws IOException {
        this.watchService = FileSystems.getDefault().newWatchService();
        this.scheduler = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "textindex-scheduler");
            t.setDaemon(true);
            return t;
        });
        this.onFileChanged = onFileChanged;
        this.debounceMs = config.getDebounceMs();
        this.watcherThread = new Thread(this::pollLoop, "textindex-watcher");
        this.watcherThread.setDaemon(true);
    }

    public void start() {
        running = true;
        watcherThread.start();
    }

    public void register(Path path) throws IOException {
        if (!Files.isDirectory(path)) return;
        try (var stream = Files.walk(path)) {
            stream.filter(Files::isDirectory)
                    .forEach(dir -> {
                        try {
                            dir.register(watchService,
                                    StandardWatchEventKinds.ENTRY_CREATE,
                                    StandardWatchEventKinds.ENTRY_MODIFY,
                                    StandardWatchEventKinds.ENTRY_DELETE,
                                    StandardWatchEventKinds.OVERFLOW);
                        } catch (IOException e) {
                            log.warn("Не удалось зарегистрировать каталог: {}", dir, e);
                        }
                    });
        }
    }

    private void pollLoop() {
        while (running && !Thread.currentThread().isInterrupted()) {
            WatchKey key;
            try {
                key = watchService.poll(100, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            if (key == null) continue;

            Path dir = (Path) key.watchable();
            boolean isOverflow = false;

            for (WatchEvent<?> event : key.pollEvents()) {
                WatchEvent.Kind<?> kind = event.kind();
                if (kind == StandardWatchEventKinds.OVERFLOW) {
                    isOverflow = true;
                    continue;
                }
                WatchEvent<Path> ev = (WatchEvent<Path>) event;
                Path name = ev.context();
                if (name == null) continue;
                Path fullPath = dir.resolve(name).toAbsolutePath().normalize();

                if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
                    if (Files.isDirectory(fullPath)) {
                        try { register(fullPath); } catch (IOException e) { log.warn("Ошибка регистрации нового каталога: {}", fullPath, e); }
                    }
                    scheduleIndex(fullPath);
                } else if (kind == StandardWatchEventKinds.ENTRY_MODIFY || kind == StandardWatchEventKinds.ENTRY_DELETE) {
                    scheduleIndex(fullPath);
                }
            }

            if (isOverflow) {
                log.warn("Обнаружено переполнение очереди событий (OVERFLOW) для {}. Отмена отложенных задач и переиндексация.", dir);
                debounceMap.keySet().removeIf(p -> p.startsWith(dir));
                scheduleIndex(dir);
            }

            key.reset();
        }
    }

    private void scheduleIndex(Path path) {
        ScheduledFuture<?> old = debounceMap.remove(path);
        if (old != null) old.cancel(false);
        ScheduledFuture<?> newTask = scheduler.schedule(() -> onFileChanged.accept(path), debounceMs, TimeUnit.MILLISECONDS);
        debounceMap.put(path, newTask);
    }

    @Override
    public void close() {
        running = false;
        watcherThread.interrupt();
        scheduler.shutdownNow();
        try { watchService.close(); } catch (IOException e) { log.warn("Ошибка закрытия WatchService", e); }
    }
}