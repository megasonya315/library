package com.library.cli;

import com.library.TextIndexFactory;
import com.library.api.IndexConfig;
import com.library.api.TextIndex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public class TextIndexCli {
    private static final Logger log = LoggerFactory.getLogger(TextIndexCli.class);
    private static final String PROMPT = "textindex> ";
    private final TextIndex index;
    private final AtomicBoolean running = new AtomicBoolean(true);

    public TextIndexCli(TextIndex index) { this.index = index; }

    public static void main(String[] args) throws Exception {
        IndexConfig config = IndexConfig.builder().build();
        TextIndex index = TextIndexFactory.createTextIndex(config);

        TextIndexCli cli = new TextIndexCli(index);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Завершение работы");
            cli.stop();
        }));

        System.out.println("Введите 'help', чтобы посмотреть доступные команды или 'quit' для выхода.");
        cli.startLoop();
    }

    public void startLoop() {
        Scanner scanner = new Scanner(System.in);
        while (running.get()) {
            System.out.print(PROMPT);
            if (!scanner.hasNextLine()) break;
            String line = scanner.nextLine().trim();
            if (line.isEmpty()) continue;
            try {
                processCommand(line);
            } catch (Exception e) {
                System.err.println("Ошибка: " + e.getMessage());
                log.error("Попытка выполнения команды не увенчалась успехом(", e);
            }
        }
        cleanup();
    }

    public void stop() { running.set(false); }

    private void processCommand(String command) throws IOException {
        String[] parts = command.split("\\s+", 2);
        String cmd = parts[0].toLowerCase();
        String arg = parts.length > 1 ? parts[1].trim() : "";

        switch (cmd) {
            case "add" -> {
                if (arg.isEmpty()) throw new IllegalArgumentException("Формат: add <path>");
                try {
                    Path path = Paths.get(arg);
                    index.addPath(path);
                    System.out.println("Добавлено: " + path.toAbsolutePath());
                } catch (InvalidPathException | IOException e) {
                    throw new IOException("Не получилось добавить путь: " + e.getMessage(), e);
                }
            }
            case "remove" -> {
                if (arg.isEmpty()) throw new IllegalArgumentException("Формат: remove <path>");
                Path path = Paths.get(arg);
                index.removePath(path);
                System.out.println("Путь исключен: " + path.toAbsolutePath());
            }
            case "search" -> {
                if (arg.isEmpty()) throw new IllegalArgumentException("Формат: search <word>");
                Set<Path> results = index.search(arg);
                if (results.isEmpty()) {
                    System.out.println("Не найдены файлы, содержащие '" + arg + "'.");
                } else {
                    System.out.println("Найдено " + results.size() + " файлов:");
                    results.forEach(p -> System.out.println("  - " + p));
                }
            }
            case "status" -> {
                System.out.println("Проиндексированные файлы: " + index.getIndexedFilesCount());
                System.out.println("Уникальные слова: " + index.getUniqueWordsCount());
            }
            case "help" -> {
                System.out.println("Доступные команды: add <path>, remove <path>, search <word>, status, help, quit");
            }
            case "quit", "exit" -> {
                System.out.println("Выходим...");
                stop();
            }
            default -> {
                System.out.println("Неизвестная команда. Введите 'help'.");
            }
        }
    }

    private void cleanup() {
        try { if (index != null) index.close(); }
        catch (Exception e) { log.error("Ошибка завершения работы", e); }
    }
}