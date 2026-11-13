package com.akto.dto.jobs;

import java.util.HashMap;
import java.util.Map;

import lombok.*;

import org.bson.codecs.pojo.annotations.BsonDiscriminator;
import org.bson.codecs.pojo.annotations.BsonIgnore;

/** Machine state of the S3 export job; the user's configuration is in S3ExportIntegration. */
@Getter
@Setter
@NoArgsConstructor
@BsonDiscriminator
@ToString
public class S3ExportJobParams extends JobParams {

    // dataset name -> end (exclusive, epoch ms, hour boundary) of the last hour fully exported
    private Map<String, Long> exportedUntilMs = new HashMap<>();
    // dataset name -> rows exported in the last run
    private Map<String, Long> lastRecordCounts = new HashMap<>();
    private int lastRunAt;
    private int lastSuccessAt;
    private String lastError;

    public S3ExportJobParams(Map<String, Long> exportedUntilMs) {
        this.exportedUntilMs = exportedUntilMs;
    }

    @Override
    public JobType getJobType() {
        return JobType.S3_EXPORT;
    }

    @BsonIgnore
    @Override
    public Class<? extends JobParams> getParamsClass() {
        return S3ExportJobParams.class;
    }
}
