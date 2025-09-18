package com.akto.dto;

import lombok.Getter;
import lombok.Setter;
import org.bson.types.ObjectId;


@Setter
@Getter
public class McpReconRequest {

    public static final String ID = "_id";
    private ObjectId _id;  // MongoDB _id as String

    public static final String ACCOUNT_ID = "accountId";
    private int accountId;

    public static final String IP_RANGE = "ipRange";
    private String ipRange;

    public static final String STARTED_AT = "startedAt";
    private int startedAt;  // Unix timestamp

    public static final String FINISHED_AT = "finishedAt";
    private int finishedAt; // Unix timestamp

    public static final String STATUS = "status";
    private String status;  // Pending, In Progress, Completed, Failed

    public static final String SERVERS_FOUND = "serversFound";
    private int serversFound;

    public static final String CREATED_AT = "createdAt";
    private int createdAt;  // Unix timestamp when request was created

    // Constructors
    public McpReconRequest() {
        // Default constructor
    }

    public McpReconRequest(ObjectId id, int accountId, String ipRange, String status) {
        this._id = id;
        this.accountId = accountId;
        this.ipRange = ipRange;
        this.status = status;
        this.startedAt = 0;
        this.finishedAt = 0;
    }

    public McpReconRequest(ObjectId id, int accountId, String ipRange, int startedAt, int finishedAt, String status, int serversFound, int createdAt) {
        this._id = id;
        this.accountId = accountId;
        this.ipRange = ipRange;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
        this.status = status;
        this.serversFound = serversFound;
        this.createdAt = createdAt;
    }

    // Status constants
    public static final String STATUS_PENDING = "Pending";
    public static final String STATUS_IN_PROGRESS = "In Progress";
    public static final String STATUS_COMPLETED = "Completed";
    public static final String STATUS_FAILED = "Failed";

    // Helper methods
    public boolean isPending() {
        return STATUS_PENDING.equals(status);
    }

    public boolean isInProgress() {
        return STATUS_IN_PROGRESS.equals(status);
    }

    public boolean isCompleted() {
        return STATUS_COMPLETED.equals(status);
    }

    public boolean isFailed() {
        return STATUS_FAILED.equals(status);
    }

    @Override
    public String toString() {
        return "McpReconRequest{" +
                "id='" + _id + '\'' +
                ", accountId=" + accountId +
                ", ipRange='" + ipRange + '\'' +
                ", startedAt=" + startedAt +
                ", finishedAt=" + finishedAt +
                ", status='" + status + '\'' +
                ", serversFound=" + serversFound +
                '}';
    }
}