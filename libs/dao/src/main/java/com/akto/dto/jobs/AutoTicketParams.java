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
public class AutoTicketParams extends JobParams {

    private static final JobType jobType = JobType.JIRA_AUTO_CREATE_TICKETS;

    private ObjectId testingRunId;
    private ObjectId summaryId;
    private String projectId;
    private String issueType;
    private List<String> severities;
    private String integrationType;

    @Override
    public JobType getJobType() {
        return jobType;
    }

    @Override
    @BsonIgnore
    public Class<? extends JobParams> getParamsClass() {
        return AutoTicketParams.class;
    }
}
