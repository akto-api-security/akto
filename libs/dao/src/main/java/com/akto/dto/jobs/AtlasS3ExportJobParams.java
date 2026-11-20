package com.akto.dto.jobs;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;
import org.bson.codecs.pojo.annotations.BsonIgnore;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@BsonDiscriminator
@ToString
public class AtlasS3ExportJobParams extends JobParams {

    // Epoch ms up to which (exclusive) Atlas traces have been exported.
    private long lastExportedTs;

    @Override
    public JobType getJobType() {
        return JobType.ATLAS_S3_EXPORT;
    }

    @BsonIgnore
    @Override
    public Class<? extends JobParams> getParamsClass() {
        return AtlasS3ExportJobParams.class;
    }
}
