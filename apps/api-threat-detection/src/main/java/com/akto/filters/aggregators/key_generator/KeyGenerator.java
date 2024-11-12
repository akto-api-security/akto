package com.akto.filters.aggregators.key_generator;

import com.akto.dto.HttpResponseParams;
import java.util.Optional;

public interface KeyGenerator {

    /*
     * Get the aggregation key.
     * Key can be something like source IP
     */
    Optional<String> generate(HttpResponseParams responseParams);
}
