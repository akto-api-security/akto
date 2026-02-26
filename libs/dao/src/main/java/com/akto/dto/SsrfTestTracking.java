package com.akto.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.bson.types.ObjectId;
import java.util.Date;

/**
 * DTO for tracking SSRF test execution and URL hits.
 * Stores UUID mappings to link SSRF URL hits with test execution context.
 * Documents are automatically deleted after 7 days via TTL index on expiresAt field.
 */
@Getter
@Setter
@NoArgsConstructor
public class SsrfTestTracking {
    
    public static final String ID = "_id";
    private ObjectId id;
    
    public static final String UUID = "uuid";
    private String uuid;
    
    public static final String ACCOUNT_ID = "accountId";
    private int accountId;
    
    public static final String TEST_RUN_ID = "testRunId";
    private ObjectId testRunId;
    
    public static final String TEST_RUN_RESULT_SUMMARY_ID = "testRunResultSummaryId";
    private ObjectId testRunResultSummaryId;
    
    public static final String API_INFO_KEY = "apiInfoKey";
    private String apiInfoKey;
    
    public static final String TEST_SUB_TYPE = "testSubType";
    private String testSubType;
    
    public static final String URL_HIT = "urlHit";
    private boolean urlHit;
    
    public static final String CREATED_AT = "createdAt";
    private int createdAt;
    
    public static final String UPDATED_AT = "updatedAt";
    private int updatedAt;
    
    public static final String EXPIRES_AT = "expiresAt";
    private Date expiresAt;

    /**
     * Constructor for creating a new SSRF test tracking entry.
     * Automatically sets expiresAt to 7 days from createdAt for TTL index.
     * 
     * @param uuid The generated UUID for tracking
     * @param accountId The account ID for the test
     * @param testRunId The test run ID
     * @param testRunResultSummaryId The test run result summary ID
     * @param apiInfoKey The API endpoint being tested (format: "METHOD /path")
     * @param testSubType The subtype of the SSRF test
     * @param createdAt Timestamp when the entry was created (Unix timestamp in seconds)
     */
    public SsrfTestTracking(String uuid, int accountId, ObjectId testRunId, ObjectId testRunResultSummaryId, 
                          String apiInfoKey, String testSubType, int createdAt) {
        this.uuid = uuid;
        this.accountId = accountId;
        this.testRunId = testRunId;
        this.testRunResultSummaryId = testRunResultSummaryId;
        this.apiInfoKey = apiInfoKey;
        this.testSubType = testSubType;
        this.urlHit = false;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
        // Set expiresAt to 7 days from createdAt for TTL index
        this.expiresAt = new Date((createdAt + (7L * 24 * 60 * 60)) * 1000L);
    }
}
