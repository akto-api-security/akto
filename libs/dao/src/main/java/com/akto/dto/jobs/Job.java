package com.akto.dto.jobs;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.bson.types.ObjectId;

@Getter
@Setter
@ToString
@NoArgsConstructor
public class Job {

    public static final String ID = "_id";
    public static final String JOB_STATUS = "jobStatus";
    public static final String HEARTBEAT_AT = "heartbeatAt";
    public static final String STARTED_AT = "startedAt";
    public static final String FINISHED_AT = "finishedAt";
    public static final String SCHEDULED_AT = "scheduledAt";
    public static final String JOB_EXECUTOR_TYPE = "jobExecutorType";
    public static final String LAST_UPDATED_AT = "lastUpdatedAt";
    public static final String RECURRING_INTERVAL_SECONDS = "recurringIntervalSeconds";
    public static final String JOB_PARAMS = "jobParams";

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
    private int lastUpdatedAt;
    private int recurringIntervalSeconds;

    public Job(
        int accountId,
        ScheduleType scheduleType,
        JobStatus jobStatus,
        JobParams jobParams,
        JobExecutorType jobExecutorType,
        int scheduledAt,
        int startedAt,
        int finishedAt,
        int heartbeatAt,
        int createdAt,
        int lastUpdatedAt) {
        this.jobParams = jobParams;
        this.accountId = accountId;
        this.scheduleType = scheduleType;
        this.jobStatus = jobStatus;
        this.jobExecutorType = jobExecutorType;
        this.scheduledAt = scheduledAt;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
        this.heartbeatAt = heartbeatAt;
        this.createdAt = createdAt;
        this.lastUpdatedAt = lastUpdatedAt;
    }

    public Job(int accountId,
        ScheduleType scheduleType,
        JobStatus jobStatus,
        JobParams jobParams,
        JobExecutorType jobExecutorType,
        int scheduledAt,
        int startedAt,
        int finishedAt,
        int heartbeatAt,
        int createdAt,
        int lastUpdatedAt,
        int recurringIntervalSeconds) {
        this.accountId = accountId;
        this.scheduleType = scheduleType;
        this.jobStatus = jobStatus;
        this.jobParams = jobParams;
        this.jobExecutorType = jobExecutorType;
        this.scheduledAt = scheduledAt;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
        this.heartbeatAt = heartbeatAt;
        this.createdAt = createdAt;
        this.lastUpdatedAt = lastUpdatedAt;
        this.recurringIntervalSeconds = recurringIntervalSeconds;
    }
}
