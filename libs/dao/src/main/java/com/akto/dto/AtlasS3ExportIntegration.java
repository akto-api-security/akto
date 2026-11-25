package com.akto.dto;

import lombok.Getter;
import lombok.Setter;
import org.bson.types.ObjectId;

/**
 * Per-account config for exporting Atlas or Argus traces (OTLP JSON) to the customer's own S3 bucket.
 * Written by the dashboard quick-start connector, read by the ATLAS_S3_EXPORT recurring job.
 */
@Getter
@Setter
public class AtlasS3ExportIntegration {

    // Stigg feature flag; the export is SaaS-only and never runs on on-prem deployments.
    public static final String FEATURE_LABEL = "ATLAS_S3_LOG_EXPORT";

    public static final String BUCKET_NAME = "bucketName";
    public static final String REGION = "region";
    public static final String PREFIX = "prefix";
    public static final String ROLE_ARN = "roleArn";
    public static final String AUTH_TYPE = "authType";
    public static final String ACCESS_KEY_ID = "accessKeyId";
    public static final String SECRET_ACCESS_KEY = "secretAccessKey";
    public static final String CREATED_TS = "createdTs";
    public static final String UPDATED_TS = "updatedTs";
    public static final String EXPORT_JOB_ID = "exportJobId";
    public static final String SOURCE = "source";

    // How Akto authenticates to the customer's bucket.
    public static final String AUTH_TYPE_ROLE = "ROLE";
    public static final String AUTH_TYPE_ACCESS_KEYS = "ACCESS_KEYS";

    private String bucketName;
    private String region;
    private String prefix;
    // ROLE (default when unset) or ACCESS_KEYS.
    private String authType;
    // ROLE: customer IAM role that trusts Akto's AWS account; assumed with ExternalId = Akto account id.
    private String roleArn;
    // ACCESS_KEYS: customer IAM user's keys. The secret is never returned to the browser.
    private String accessKeyId;
    private String secretAccessKey;
    private ObjectId exportJobId;
    // Dashboard context the export belongs to: ENDPOINT (Atlas traces) or AGENTIC (Argus traces).
    private String source;

    private int createdTs;
    private int updatedTs;

    public boolean usesAccessKeys() {
        return AUTH_TYPE_ACCESS_KEYS.equals(authType);
    }
}
