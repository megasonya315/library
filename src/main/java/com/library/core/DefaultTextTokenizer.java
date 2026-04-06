package com.library.core;

import com.library.api.TextTokenizer;

import java.util.logging.Logger;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class DefaultTextTokenizer implements TextTokenizer {

    private static final Logger log = Logger.getLogger(DefaultTextTokenizer.class.getName());
    private static final Pattern WORD_PATTERN = Pattern.compile("\\p{L}[\\p{L}\\p{N}_]*");

    @Override
    public Stream<String> tokenize(CharSequence text) {
        if (text == null || text.isEmpty()) {
            return Stream.empty();
        }
        Matcher matcher = WORD_PATTERN.matcher(text);
        return matcher.results()
                .map(MatchResult::group)
                .map(String::toLowerCase);
    }
}
