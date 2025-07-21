package com.akto.threat.backend.db;

import java.util.Map;

@lombok.Getter
@lombok.Setter
@lombok.AllArgsConstructor
@lombok.NoArgsConstructor
public class ApiDistributionDataModel {
    
    public int apiCollectionId;
    public String url;
    public String method;
    public int windowSize;
    public long windowStart;
    public Map<String, Integer> distribution;

}
