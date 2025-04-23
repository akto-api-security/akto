package com.akto.dto.jobs;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.bson.types.ObjectId;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class Job {

    public static final String ID = "_id";
    public static final String JOB_STATUS = "jobStatus";
    public static final String HEARTBEAT_AT = "heartbeatAt";
    public static final String FINISHED_AT = "finishedAt";
    public static final String SCHEDULED_AT = "scheduledAt";
    public static final String JOB_EXECUTOR_TYPE = "jobExecutorType";

    private ObjectId id;
    private int accountId;
    private ScheduleType scheduleType;
    private JobStatus jobStatus;
    private JobParams jobParams;
    private JobExecutorType jobExecutorType;
    private int scheduledAt;
    private int startedAt;
    private int finishedAt;
    private int heartbeatAt;
    private int createdAt;
}
