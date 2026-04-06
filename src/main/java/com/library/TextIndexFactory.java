package com.library;

import com.library.api.IndexConfig;
import com.library.api.TextIndex;
import com.library.api.TextTokenizer;
import com.library.core.DefaultTextTokenizer;
import com.library.core.InMemoryTextIndex;

import java.io.IOException;

public final class TextIndexFactory {

    private TextIndexFactory() {}

    public static TextIndex createTextIndex(IndexConfig config) throws IOException {
        TextTokenizer tokenizer = config.getTokenizer() != null ? config.getTokenizer() : new DefaultTextTokenizer();
        return new InMemoryTextIndex(config, tokenizer);
    }

}
