package com.akto.threat.detection.db.entity;

import com.akto.dto.type.URLMethods;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.PrePersist;
import javax.persistence.Table;

import org.hibernate.annotations.GenericGenerator;

@Entity
@Table(name = "malicious_event", schema = "threat_detection")
public class MaliciousEventEntity {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    private UUID id;

    @Column(name = "actor")
    private String actor;

    @Column(name = "filter_id")
    private String filterId;

    @Column(name = "url")
    private String url;

    @Column(name = "method")
    @Enumerated(EnumType.STRING)
    private URLMethods.Method method;

    @Column(name = "timestamp")
    private long timestamp;

    @Column(name = "orig")
    private String orig;

    // Geo location data
    @Column(name = "ip")
    private String ip;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public MaliciousEventEntity() {
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    public MaliciousEventEntity(Builder builder) {
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

        public Builder setActor(String actorId) {
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

        public MaliciousEventEntity build() {
            return new MaliciousEventEntity(this);
        }
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public UUID getId() {
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

    public LocalDateTime getCreatedAt() {
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
