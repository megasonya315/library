package com.library.core;

import com.library.api.IndexConfig;
import com.library.api.TextIndex;
import com.library.api.TextTokenizer;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;

//TODO: реализовать ConcurrentHashMap + ReadWriteLock + FileWatcher
public class InMemoryTextIndex implements TextIndex {

    public InMemoryTextIndex(IndexConfig config, TextTokenizer tokenizer) {}

    @Override public void addPath(Path path) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void removePath(Path path) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public Set<Path> search(String word) {
        return Set.of();
    }

    @Override
    public long getIndexedFilesCount() {
        return 0;
    }

    @Override
    public long getUniqueWordsCount() {
        return 0;
    }

    @Override
    public void close() throws Exception {}
}
