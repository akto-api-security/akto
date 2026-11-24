package com.akto.dto.redteam_learnings;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * What every red-team learning record is about: one target agent, identified by
 * {apiCollectionId, url, method}, and the testing run summary it came from.
 *
 * Absent, null and empty mean the same thing across the Java, Node and dashboard sides:
 * a list that is not stored reads back as an empty list, and a String is either a real
 * value or null. Writers never store an empty String, and a missing optional value is
 * never replaced by a made-up one (an unknown verdict is null, not "0").
 */
@Getter
@Setter
@NoArgsConstructor
public abstract class AgentRedteamRecord {

    public static final String API_COLLECTION_ID = "apiCollectionId";
    public static final String URL = "url";
    public static final String METHOD = "method";
    public static final String TESTING_RUN_RESULT_SUMMARY_ID = "testingRunResultSummaryId";
    public static final String CREATED_AT_MS = "createdAtMs";

    /** Deterministic per record type (see each subclass), so a retried write replaces itself instead of adding a copy. */
    private String id;
    private int apiCollectionId;
    private String url;
    private String method;
    private String testingRunResultSummaryId;
    /** When the record was written, in milliseconds. */
    private long createdAtMs;
}
