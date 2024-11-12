package com.akto.filters.aggregators.key_generator;

import com.akto.dto.HttpResponseParams;

import java.util.Optional;

public class SourceIPKeyGenerator implements KeyGenerator {

    private SourceIPKeyGenerator() {
    }

    public static SourceIPKeyGenerator instance = new SourceIPKeyGenerator();

    @Override
    public Optional<String> generate(HttpResponseParams responseParams) {
        return Optional.of(responseParams.getSourceIP());
    }
}
