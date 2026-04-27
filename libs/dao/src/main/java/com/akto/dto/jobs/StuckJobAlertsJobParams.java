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
public class StuckJobAlertsJobParams extends JobParams {

    private int slackWebhookId;
    private int stuckThresholdSeconds;

    @Override
    public JobType getJobType() {
        return JobType.STUCK_JOB_ALERTS;
    }

    @BsonIgnore
    @Override
    public Class<? extends JobParams> getParamsClass() {
        return StuckJobAlertsJobParams.class;
    }
}
