package com.akto.dto.threat_detection;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor

public class ApiHitCountInfo {
    private int apiCollectionId;
    private String url;
    private String method;
    private long count;
    private long ts;
}
