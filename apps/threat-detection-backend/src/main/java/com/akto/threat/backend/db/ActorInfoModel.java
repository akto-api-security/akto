package com.akto.threat.backend.db;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActorInfoModel {
    private ObjectId id;  // MongoDB _id for cursor-based pagination
    private String actorId;  // Actor identifier (IP, user ID, API key, etc.)
    private String filterId;
    private String category;
    private int apiCollectionId;
    private String url;
    private String method;
    private String country;
    private String severity;
    private String host;
    private String contextSource;  // Context: API, ENDPOINT, AGENTIC, etc.
    private long discoveredAt;
    private long updatedAt;
    private long lastAttackTs;
    private int totalAttacks;
    private String status;
    private boolean isCritical;  // True if actor has HIGH or CRITICAL severity attacks
    private String latestMetadata;  // Metadata from latest attack (JSON string)

    // Backward compatibility - maps actorId to ip
    public String getIp() {
        return actorId;
    }

    public void setIp(String ip) {
        this.actorId = ip;
    }

    // Backward compatibility - maps updatedAt to updatedTs
    public long getUpdatedTs() {
        return updatedAt;
    }

    // Backward compatibility - old code uses newBuilder()
    public static ActorInfoModelBuilder newBuilder() {
        return builder();
    }

    // Custom Builder inner class to maintain backward compatibility
    public static class ActorInfoModelBuilder {
        // Backward compatibility for builder
        public ActorInfoModelBuilder ip(String ip) {
            this.actorId = ip;
            return this;
        }

        public ActorInfoModelBuilder updatedTs(long updatedTs) {
            this.updatedAt = updatedTs;
            return this;
        }
    }
}
