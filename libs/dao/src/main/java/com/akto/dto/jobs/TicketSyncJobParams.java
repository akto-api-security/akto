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
public class TicketSyncJobParams extends JobParams {

    private String ticketSource;
    private String projectKey;
    private int lastSyncedAt;

    @Override
    public JobType getJobType() {
        return JobType.TICKET_SYNC;
    }

    @BsonIgnore
    @Override
    public Class<? extends JobParams> getParamsClass() {
        return TicketSyncJobParams.class;
    }
}
