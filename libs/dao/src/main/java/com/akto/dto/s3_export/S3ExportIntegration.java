package com.akto.dto.s3_export;

import java.util.List;

import org.bson.types.ObjectId;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Per-account S3 export configuration (singleton document). Export progress lives on the
 * recurring job's params (S3ExportJobParams), not here.
 */
@Getter
@Setter
@NoArgsConstructor
public class S3ExportIntegration {

    public static final String BUCKET = "bucket";
    public static final String REGION = "region";
    public static final String PREFIX = "prefix";
    public static final String ROLE_ARN = "roleArn";
    public static final String EXTERNAL_ID = "externalId";
    public static final String KMS_KEY_ARN = "kmsKeyArn";
    public static final String DATASETS = "datasets";
    public static final String INCLUDE_PAYLOADS = "includePayloads";
    public static final String INTERVAL_HOURS = "intervalHours";
    public static final String ENABLED = "enabled";
    public static final String JOB_ID = "jobId";
    public static final String CREATED_AT = "createdAt";
    public static final String UPDATED_AT = "updatedAt";
    public static final String UPDATED_BY = "updatedBy";

    public static final int DEFAULT_INTERVAL_HOURS = 1;

    private String bucket;
    private String region;
    private String prefix;
    private String roleArn;
    // server-generated; the customer puts it in the role's trust policy
    private String externalId;
    private String kmsKeyArn;
    private List<String> datasets;
    private boolean includePayloads = true;
    private int intervalHours = DEFAULT_INTERVAL_HOURS;
    private boolean enabled;
    private ObjectId jobId;
    private int createdAt;
    private int updatedAt;
    private String updatedBy;
}
