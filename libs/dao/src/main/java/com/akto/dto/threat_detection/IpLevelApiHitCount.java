package com.akto.dto.threat_detection;

import java.util.Map;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor

public class IpLevelApiHitCount {
    private int apiCollectionId;
    private String url;
    private String method;
    private long count;
    private long ts;
    Map<String, Integer> ipCount;
}
