package com.akto.dto.threat_detection;

import java.util.List;
import java.util.Map;

import org.bson.codecs.pojo.annotations.BsonId;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GuardrailComplianceInfo {

    @BsonId
    private String id;

    public static final String MAP_COMPLIANCE_TO_LIST_CLAUSES = "mapComplianceToListClauses";
    private Map<String, List<String>> mapComplianceToListClauses;

    public static final String AUTHOR = "author";
    private String author;

    public static final String HASH = "hash";
    private int hash;

    private String sourcePath;
}
