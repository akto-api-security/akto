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

    public AutoTicketParams() {
        super.jobType = jobType;
    }

    public AutoTicketParams(ObjectId testingRunId, ObjectId summaryId, String projectId, String issueType,
        List<String> severities, String integrationType) {
        this();
        this.testingRunId = testingRunId;
        this.summaryId = summaryId;
        this.projectId = projectId;
        this.issueType = issueType;
        this.severities = severities;
        this.integrationType = integrationType;
    }

    @Override
    @BsonIgnore
    public Class<? extends JobParams> getParamsClass() {
        return AutoTicketParams.class;
    }
}
