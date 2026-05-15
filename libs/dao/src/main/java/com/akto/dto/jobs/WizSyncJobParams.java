package com.akto.dto.jobs;

import lombok.*;

import org.bson.codecs.pojo.annotations.BsonDiscriminator;
import org.bson.codecs.pojo.annotations.BsonIgnore;

@Getter
@Setter
@NoArgsConstructor
@BsonDiscriminator
@ToString
public class WizSyncJobParams extends JobParams {

    @Override
    public JobType getJobType() {
        return JobType.WIZ_SYNC;
    }

    @BsonIgnore
    @Override
    public Class<? extends JobParams> getParamsClass() {
        return WizSyncJobParams.class;
    }
}
