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
public class PortIOAutoTicketParams extends JobParams {

    private static final JobType jobType = JobType.PORT_IO_AUTO_CREATE_TICKETS;

    @JsonIgnore
    private ObjectId testingRunId;

    @BsonIgnore
    private String testingRunHexId;

    @JsonIgnore
    private ObjectId summaryId;

    @BsonIgnore
    private String summaryHexId;

    private List<String> severities;

    public PortIOAutoTicketParams() {
        super.jobType = jobType;
    }

    public PortIOAutoTicketParams(ObjectId testingRunId, ObjectId summaryId, List<String> severities) {
        this();
        this.testingRunId = testingRunId;
        this.testingRunHexId = testingRunId.toHexString();
        this.summaryId = summaryId;
        this.summaryHexId = summaryId.toHexString();
        this.severities = severities;
    }

    @Override
    @BsonIgnore
    public Class<? extends JobParams> getParamsClass() {
        return PortIOAutoTicketParams.class;
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
