package com.akto.dto.api_protection_parse_layer;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class Condition {

    private int matchCount;
    private int windowThreshold;
    private String incrementFilter;
    private String thresholdBreachFilter;
    private DistinctIdentifier distinctIdentifier;
    private ValueSource groupBy;

    public Condition(int matchCount, int windowThreshold) {
        this.matchCount = matchCount;
        this.windowThreshold = windowThreshold;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ValueSource {
        private String source; // "request_payload", "response_payload", "request_headers"
        private String key;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class DistinctIdentifier extends ValueSource {
        private int count;
        private String attribute; // built-in value used instead of source/key, e.g. "country_code"

        public DistinctIdentifier(int count, String source, String key) {
            super(source, key);
            this.count = count;
        }
    }
}
