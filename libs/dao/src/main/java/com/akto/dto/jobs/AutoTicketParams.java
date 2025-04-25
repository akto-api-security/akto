package com.akto.dto.jobs;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.List;
import lombok.Getter;
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

    @JsonIgnore
    private ObjectId testingRunId;

    @BsonIgnore
    private String testingRunHexId;

    @JsonIgnore
    private ObjectId summaryId;

    @BsonIgnore
    private String summaryHexId;

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
        this.testingRunHexId = testingRunId.toHexString();
        this.summaryId = summaryId;
        this.summaryHexId = summaryId.toHexString();
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

    public void setTestingRunHexId(String testingRunHexId) {
        this.testingRunHexId = testingRunHexId;
        if (testingRunHexId != null) {
            this.testingRunId = new ObjectId(testingRunHexId);
        }
    }

    public void setSummaryHexId(String summaryHexId) {
        this.summaryHexId = summaryHexId;
        if (summaryHexId != null) {
            this.summaryId = new ObjectId(summaryHexId);
        }
    }
}
