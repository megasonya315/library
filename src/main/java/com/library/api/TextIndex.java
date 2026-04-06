package com.library.api;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;

public interface TextIndex extends AutoCloseable {

    void addPath(Path path) throws IOException;
    void removePath(Path path) throws IOException;

    Set<Path> search(String word);

    long getIndexedFilesCount();
    long getUniqueWordsCount();
}
