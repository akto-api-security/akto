package com.akto.filters.aggregators.key_generator;

import com.akto.dto.HttpResponseParams;
import com.akto.runtime.policies.ApiAccessTypePolicy;

import java.util.List;
import java.util.Optional;

public class SourceIPKeyGenerator implements KeyGenerator {

    private SourceIPKeyGenerator() {
    }

    public static SourceIPKeyGenerator instance = new SourceIPKeyGenerator();

    @Override
    public Optional<String> generate(HttpResponseParams responseParams) {
        List<String> sourceIPs = ApiAccessTypePolicy.getSourceIps(responseParams);
        if (sourceIPs.isEmpty()) {
            return Optional.of(responseParams.getSourceIP());
        }

        return Optional.of(sourceIPs.get(0));
    }
}
