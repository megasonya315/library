package com.library.api;

import java.util.stream.Stream;

public interface TextTokenizer {
    Stream<String> tokenize(CharSequence text);
}
