package com.akto.threat.detection.db.entity;

import com.akto.dto.type.URLMethods;

import java.time.LocalDate;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name = "malicious_event", schema = "threat_detection")
public class MaliciousEvent {

    @Id
    @GeneratedValue(strategy =  GenerationType.AUTO)
    private String id;

    @Column(name = "actor")
    private String actor;

    @Column(name = "filter_id")
    private String filterId;

    @Column(name = "url")
    private String url;

    @Column(name = "method")
    private URLMethods.Method method;

    @Column(name = "timestamp")
    private long timestamp;

    @Column(name = "orig")
    private String orig;

    // Geo location data
    @Column(name = "ip")
    private String ip;

    @Column(name = "created_at")
    private LocalDate createdAt;

    public MaliciousEvent() {
    }

    public MaliciousEvent(Builder builder) {
        this.actor = builder.actorId;
        this.filterId = builder.filterId;
        this.url = builder.url;
        this.method = builder.method;
        this.timestamp = builder.timestamp;
        this.orig = builder.orig;
        this.ip = builder.ip;
    }

    public static class Builder {
        private String actorId;
        private String filterId;
        private String url;
        private URLMethods.Method method;
        private long timestamp;
        private String orig;
        private String ip;

        public Builder setActorId(String actorId) {
            this.actorId = actorId;
            return this;
        }

        public Builder setFilterId(String filterId) {
            this.filterId = filterId;
            return this;
        }

        public Builder setUrl(String url) {
            this.url = url;
            return this;
        }

        public Builder setMethod(URLMethods.Method method) {
            this.method = method;
            return this;
        }

        public Builder setTimestamp(long timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder setOrig(String orig) {
            this.orig = orig;
            return this;
        }

        public Builder setIp(String ip) {
            this.ip = ip;
            return this;
        }
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public String getId() {
        return id;
    }

    public String getActor() {
        return actor;
    }

    public String getFilterId() {
        return filterId;
    }

    public String getUrl() {
        return url;
    }

    public URLMethods.Method getMethod() {
        return method;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getOrig() {
        return orig;
    }

    public String getIp() {
        return ip;
    }

    public LocalDate getCreatedAt() {
        return createdAt;
    }

    @Override
    public String toString() {
        return "MaliciousEventModel{"
                + "id='"
                + id
                + '\''
                + ", actorId='"
                + actor
                + '\''
                + ", filterId='"
                + filterId
                + '\''
                + ", url='"
                + url
                + '\''
                + ", method="
                + method
                + ", timestamp="
                + timestamp
                + ", orig='"
                + orig
                + '\''
                + ", ip='"
                + ip
                + '\''
                + '}';
    }
}
