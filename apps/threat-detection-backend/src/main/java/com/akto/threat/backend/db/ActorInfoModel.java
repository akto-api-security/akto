package com.akto.threat.backend.db;

public class ActorInfoModel {
    private String ip;
    private long updatedTs;
    private String status;
    public ActorInfoModel(String ip, long updatedTs, String status) {
        this.ip = ip;
        this.updatedTs = updatedTs;
        this.status = status;
    }
    
    public ActorInfoModel(Builder builder) {
        this.ip = builder.ip;
        this.updatedTs = builder.updatedTs;
        this.status = builder.status;
    }

    public static class Builder {
        private String ip;
        private long updatedTs;
        private String status;

        public Builder setIp(String ip) {
            this.ip = ip;
            return this;
        }

        public Builder setUpdatedTs(long updatedTs) {
            this.updatedTs = updatedTs;
            return this;
        }

        public Builder setStatus(String status) {
            this.status = status;
            return this;
        }
        
        public ActorInfoModel build() {
            return new ActorInfoModel(this);
        }
    }

    public String getIp() {
        return ip;
    }

    public long getUpdatedTs() {
        return updatedTs;
    }    

    public String getStatus() {
        return status;
    }

    public static Builder newBuilder() {
        return new Builder();
    }
}
