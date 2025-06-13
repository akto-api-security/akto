package com.akto.dto.jobs;

import com.akto.dto.notifications.CustomWebhook;
import lombok.*;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;
import org.bson.codecs.pojo.annotations.BsonIgnore;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@BsonDiscriminator
@ToString
public class PendingTestsAlertsJobParams extends JobParams {


    private int lastSyncedAt;
    private int customWebhookId;

    @Override
    public JobType getJobType() {
        return JobType.PENDING_TESTS_ALERTS;
    }

    @BsonIgnore
    @Override
    public Class<? extends JobParams> getParamsClass() {
        return PendingTestsAlertsJobParams.class;
    }
}
