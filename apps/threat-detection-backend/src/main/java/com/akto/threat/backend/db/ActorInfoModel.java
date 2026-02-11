package com.akto.threat.backend.db;

public class ActorInfoModel {
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

    // Public no-arg constructor required by MongoDB POJO codec
    public ActorInfoModel() {
    }

    public ActorInfoModel(String actorId, long updatedTs, String status) {
        this.actorId = actorId;
        this.updatedAt = updatedTs;
        this.status = status;
    }

    public ActorInfoModel(Builder builder) {
        this.actorId = builder.actorId;
        this.filterId = builder.filterId;
        this.category = builder.category;
        this.apiCollectionId = builder.apiCollectionId;
        this.url = builder.url;
        this.method = builder.method;
        this.country = builder.country;
        this.severity = builder.severity;
        this.host = builder.host;
        this.contextSource = builder.contextSource;
        this.discoveredAt = builder.discoveredAt;
        this.updatedAt = builder.updatedAt;
        this.lastAttackTs = builder.lastAttackTs;
        this.totalAttacks = builder.totalAttacks;
        this.status = builder.status;
    }

    public static class Builder {
        private String actorId;
        private String filterId;
        private String category;
        private int apiCollectionId;
        private String url;
        private String method;
        private String country;
        private String severity;
        private String host;
        private String contextSource;
        private long discoveredAt;
        private long updatedAt;
        private long lastAttackTs;
        private int totalAttacks;
        private String status;

        public Builder setActorId(String actorId) {
            this.actorId = actorId;
            return this;
        }

        // Backward compatibility - maps ip to actorId
        public Builder setIp(String ip) {
            this.actorId = ip;
            return this;
        }

        public Builder setFilterId(String filterId) {
            this.filterId = filterId;
            return this;
        }

        public Builder setCategory(String category) {
            this.category = category;
            return this;
        }

        public Builder setApiCollectionId(int apiCollectionId) {
            this.apiCollectionId = apiCollectionId;
            return this;
        }

        public Builder setUrl(String url) {
            this.url = url;
            return this;
        }

        public Builder setMethod(String method) {
            this.method = method;
            return this;
        }

        public Builder setCountry(String country) {
            this.country = country;
            return this;
        }

        public Builder setSeverity(String severity) {
            this.severity = severity;
            return this;
        }

        public Builder setHost(String host) {
            this.host = host;
            return this;
        }

        public Builder setContextSource(String contextSource) {
            this.contextSource = contextSource;
            return this;
        }

        public Builder setDiscoveredAt(long discoveredAt) {
            this.discoveredAt = discoveredAt;
            return this;
        }

        public Builder setUpdatedAt(long updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public Builder setLastAttackTs(long lastAttackTs) {
            this.lastAttackTs = lastAttackTs;
            return this;
        }

        public Builder setTotalAttacks(int totalAttacks) {
            this.totalAttacks = totalAttacks;
            return this;
        }

        public Builder setStatus(String status) {
            this.status = status;
            return this;
        }

        // Backward compatibility - maps updatedTs to updatedAt
        public Builder setUpdatedTs(long updatedTs) {
            this.updatedAt = updatedTs;
            return this;
        }

        public ActorInfoModel build() {
            return new ActorInfoModel(this);
        }
    }

    public String getActorId() {
        return actorId;
    }

    // Backward compatibility - maps actorId to ip
    public String getIp() {
        return actorId;
    }

    public String getFilterId() {
        return filterId;
    }

    public String getCategory() {
        return category;
    }

    public int getApiCollectionId() {
        return apiCollectionId;
    }

    public String getUrl() {
        return url;
    }

    public String getMethod() {
        return method;
    }

    public String getCountry() {
        return country;
    }

    public String getSeverity() {
        return severity;
    }

    public String getHost() {
        return host;
    }

    public String getContextSource() {
        return contextSource;
    }

    public long getDiscoveredAt() {
        return discoveredAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public long getLastAttackTs() {
        return lastAttackTs;
    }

    public int getTotalAttacks() {
        return totalAttacks;
    }

    public String getStatus() {
        return status;
    }

    // Backward compatibility
    public long getUpdatedTs() {
        return updatedAt;
    }

    // Public setters required by MongoDB POJO codec
    public void setActorId(String actorId) {
        this.actorId = actorId;
    }

    // Backward compatibility - maps ip to actorId
    public void setIp(String ip) {
        this.actorId = ip;
    }

    public void setFilterId(String filterId) {
        this.filterId = filterId;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public void setApiCollectionId(int apiCollectionId) {
        this.apiCollectionId = apiCollectionId;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public void setContextSource(String contextSource) {
        this.contextSource = contextSource;
    }

    public void setDiscoveredAt(long discoveredAt) {
        this.discoveredAt = discoveredAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }

    public void setLastAttackTs(long lastAttackTs) {
        this.lastAttackTs = lastAttackTs;
    }

    public void setTotalAttacks(int totalAttacks) {
        this.totalAttacks = totalAttacks;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public static Builder newBuilder() {
        return new Builder();
    }
}
