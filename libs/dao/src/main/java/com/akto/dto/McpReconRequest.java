package com.akto.dto;

import lombok.Getter;
import lombok.Setter;
import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.bson.types.ObjectId;


@Setter
@Getter
public class McpReconRequest {

    public static final String ID = "_id";
    private ObjectId id;

    @BsonIgnore
    private String hexId; // Hex string representation of the ObjectId for API responses

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

    // Get hex string representation of the ObjectId for API responses
    public String getHexId() {
        if (this.hexId == null && this.id != null) {
            this.hexId = this.id.toHexString();
        }
        return this.hexId;
    }

    @Override
    public String toString() {
        return "McpReconRequest{" +
                "_id=" + (id != null ? id.toHexString() : "null") +
                ", accountId=" + accountId +
                ", ipRange='" + ipRange + '\'' +
                ", startedAt=" + startedAt +
                ", finishedAt=" + finishedAt +
                ", status='" + status + '\'' +
                ", serversFound=" + serversFound +
                ", createdAt=" + createdAt +
                '}';
    }
}
