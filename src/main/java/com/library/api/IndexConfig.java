package com.library.api;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;


public final class IndexConfig {
    private final Charset charset;
    private final TextTokenizer tokenizer;
    private final int maxThreads;
    private final int debounceMs;
    private final long maxFileSizeBytes;

    private IndexConfig(Builder builder) {
        this.charset = builder.charset;
        this.tokenizer = builder.tokenizer;
        this.maxThreads = builder.maxThreads;
        this.debounceMs = builder.debounceMs;
        this.maxFileSizeBytes = builder.maxFileSizeBytes;
    }

    public static Builder builder() { return new Builder(); }

    public Charset getCharset() { return charset; }
    public TextTokenizer getTokenizer() { return tokenizer; }
    public int getMaxThreads() { return maxThreads; }
    public int getDebounceMs() { return debounceMs; }
    public long getMaxFileSizeBytes() { return maxFileSizeBytes; }

    public static final class Builder {
        private Charset charset = StandardCharsets.UTF_8;
        private TextTokenizer tokenizer;
        private int maxThreads = Runtime.getRuntime().availableProcessors();
        private int debounceMs = 200;
        private long maxFileSizeBytes = 100 * 1024 * 1024;

        private Builder() {}

        public Builder charset(Charset charset) {
            this.charset = charset != null ? charset : StandardCharsets.UTF_8;
            return this;
        }

        public Builder tokenizer(TextTokenizer tokenizer) {
            this.tokenizer = tokenizer;
            return this;
        }

        public Builder maxThreads(int maxThreads) {
            if (maxThreads < 1) throw new IllegalArgumentException("Должен быть хотя бы 1 поток");
            this.maxThreads = maxThreads;
            return this;
        }

        public Builder debounceMs(int debounceMs) {
            if (debounceMs < 0) throw new IllegalArgumentException("Дебаунс не может быть < 0");
            this.debounceMs = debounceMs;
            return this;
        }

        public Builder maxFileSizeBytes(long maxFileSizeBytes) {
            if (maxFileSizeBytes < 0) throw new IllegalArgumentException("Максимальный размер файла не может быть < 0");
            this.maxFileSizeBytes = maxFileSizeBytes;
            return this;
        }

        public IndexConfig build() {
            return new IndexConfig(this);
        }
    }
}
