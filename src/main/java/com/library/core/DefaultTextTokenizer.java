package com.library.core;

import com.library.api.TextTokenizer;

import java.util.stream.Stream;

//TODO: реализовать на базе Pattern и Matcher.results()
public class DefaultTextTokenizer implements TextTokenizer {
    @Override
    public Stream<String> tokenize(CharSequence text) {
        throw new UnsupportedOperationException("Пока не реализовано, но скоро будет");
    }
}
