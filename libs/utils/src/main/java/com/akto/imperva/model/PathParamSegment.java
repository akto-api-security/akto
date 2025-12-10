package com.akto.imperva.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PathParamSegment {
    private Integer index;
    private SegmentDetail[] segmentDetails;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SegmentDetail {
        private String dataType; // UUID, NUMBER, WORD, HEX, etc.
    }
}