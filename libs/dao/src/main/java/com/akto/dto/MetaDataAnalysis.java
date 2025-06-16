package com.akto.dto;

import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MetaDataAnalysis {

    public static final String ANALYSIS_TYPE = "analysisType";
    public static final String ANALYSIS_DATA = "analysisData";
    public static final String LAST_SYNCED_AT = "lastSyncedAt";

    private Map<String, Object> analysisData;
    private String analysisType;
    private int lastSyncedAt;
}
