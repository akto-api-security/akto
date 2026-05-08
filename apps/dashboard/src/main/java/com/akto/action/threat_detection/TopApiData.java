package com.akto.action.threat_detection;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TopApiData {
    private String endpoint;
    private String method;
    private int attacks;
    private String severity;
}
