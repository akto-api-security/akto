package com.akto.dto.testing.config;

import lombok.Getter;

/**
 * Listing view of a library script. Never carries the script body: bodies can hold signing secrets
 * and are fetched one at a time via fetchScript.
 */
@Getter
public class TestScriptSummary {

    private final String id;
    private final String name;
    private final TestScript.Type type;
    private final String createdBy;
    private final String lastUpdatedBy;
    private final int lastCreatedAt;
    private final int lastUpdatedAt;

    private TestScriptSummary(TestScript script) {
        this.id = script.getId();
        this.name = script.getName();
        this.type = script.getType();
        this.createdBy = script.getCreatedBy();
        this.lastUpdatedBy = script.getLastUpdatedBy();
        this.lastCreatedAt = script.getLastCreatedAt();
        this.lastUpdatedAt = script.getLastUpdatedAt();
    }

    public static TestScriptSummary from(TestScript script) {
        return new TestScriptSummary(script);
    }
}
