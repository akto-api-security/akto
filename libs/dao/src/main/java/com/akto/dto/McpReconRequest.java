package com.akto.dto;

import lombok.Getter;
import lombok.Setter;
import org.bson.codecs.pojo.annotations.BsonId;

/**
 * DTO for MCP Recon Requests
 * Represents a network scan request for MCP server discovery
 */
@Setter
@Getter
public class McpReconRequest {

    // Getters and Setters
    @BsonId
    private String _id;  // MongoDB _id as String

    private int accountId;

    private String ipRange;

    private int startedAt;  // Unix timestamp

    private int finishedAt; // Unix timestamp

    private String status;  // Pending, In Progress, Completed, Failed

    private int serversFound;

    private int createdAt;  // Unix timestamp when request was created

    // Constructors
    public McpReconRequest() {
        // Default constructor
    }

    public McpReconRequest(int accountId, String ipRange, String status, int createdAt) {
        this.accountId = accountId;
        this.ipRange = ipRange;
        this.status = status;
        this.startedAt = 0;
        this.finishedAt = 0;
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