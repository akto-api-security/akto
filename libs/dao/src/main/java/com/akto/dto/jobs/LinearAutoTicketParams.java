package com.akto.dto.jobs;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;
import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.bson.types.ObjectId;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@BsonDiscriminator
@ToString
public class LinearAutoTicketParams extends JobParams {

    private ObjectId testingRunId;
    private ObjectId summaryId;
    private String teamId;
    private List<String> severities;

    @Override
    public JobType getJobType() {
        return JobType.LINEAR_AUTO_CREATE_TICKETS;
    }

    @BsonIgnore
    @Override
    public Class<? extends JobParams> getParamsClass() {
        return LinearAutoTicketParams.class;
    }
}
