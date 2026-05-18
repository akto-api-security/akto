package com.akto.dto.nhi_governance;

import java.util.List;
import java.util.Map;
import org.bson.types.ObjectId;

public class NhiViolation {
    public static final String COLLECTION_NAME = "nhi_violations";

    // Field names for MongoDB
    public static final String ID = "_id";
    public static final String VIOLATION_TYPE = "violationType";
    public static final String IDENTITIES = "identities";
    public static final String AGENT_NAME = "agentName";
    public static final String AGENT_TYPE = "agentType";
    public static final String POLICY = "policy";
    public static final String SEVERITY = "severity";
    public static final String STATUS = "status";
    public static final String CONTEXT_SOURCE = "contextSource";
    public static final String DISCOVERED_AT = "discoveredAt";
    public static final String ACKNOWLEDGED_AT = "acknowledgedAt";
    public static final String ACKNOWLEDGED_BY = "acknowledgedBy";
    public static final String RESOLVED_AT = "resolvedAt";
    public static final String RESOLVED_BY = "resolvedBy";
    public static final String UPDATED_AT = "updatedAt";
    public static final String UPDATED_BY = "updatedBy";
    public static final String DESCRIPTION = "description";
    public static final String AFFECTED_RESOURCES = "affectedResources";
    public static final String BLAST_RADIUS = "blastRadius";
    public static final String REMEDIATION_STEPS = "remediationSteps";
    public static final String TIMELINE = "timeline";
    public static final String ACKNOWLEDGMENT_NOTES = "acknowledgmentNotes";
    public static final String RESOLUTION_NOTES = "resolutionNotes";
    public static final String RESOLUTION_TYPE = "resolutionType";
    public static final String METADATA = "metadata";

    private ObjectId id;
    private String violationType;
    private List<IdentityReference> identities;
    private String agentName;
    private String agentType;
    private List<String> policy;
    private String severity;
    private String status;
    private String contextSource;
    private int discoveredAt;
    private Integer acknowledgedAt;
    private String acknowledgedBy;
    private Integer resolvedAt;
    private String resolvedBy;
    private Integer updatedAt;
    private String updatedBy;
    private String description;
    private List<String> affectedResources;
    private List<String> blastRadius;
    private List<String> remediationSteps;
    private List<TimelineEntry> timeline;
    private String acknowledgmentNotes;
    private String resolutionNotes;
    private String resolutionType;
    private Map<String, Object> metadata;

    public NhiViolation() {}

    public NhiViolation(String violationType, List<IdentityReference> identities, String agentName, String severity) {
        this.violationType = violationType;
        this.identities = identities;
        this.agentName = agentName;
        this.severity = severity;
    }

    // Getters and Setters
    public ObjectId getId() {
        return id;
    }

    public void setId(ObjectId id) {
        this.id = id;
    }

    public String getHexId() {
        return this.id != null ? this.id.toHexString() : null;
    }

    public String getViolationType() {
        return violationType;
    }

    public void setViolationType(String violationType) {
        this.violationType = violationType;
    }

    public List<IdentityReference> getIdentities() {
        return identities;
    }

    public void setIdentities(List<IdentityReference> identities) {
        this.identities = identities;
    }

    public String getAgentName() {
        return agentName;
    }

    public void setAgentName(String agentName) {
        this.agentName = agentName;
    }

    public String getAgentType() {
        return agentType;
    }

    public void setAgentType(String agentType) {
        this.agentType = agentType;
    }

    public List<String> getPolicy() {
        return policy;
    }

    public void setPolicy(List<String> policy) {
        this.policy = policy;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getContextSource() {
        return contextSource;
    }

    public void setContextSource(String contextSource) {
        this.contextSource = contextSource;
    }

    public int getDiscoveredAt() {
        return discoveredAt;
    }

    public void setDiscoveredAt(int discoveredAt) {
        this.discoveredAt = discoveredAt;
    }

    public Integer getAcknowledgedAt() {
        return acknowledgedAt;
    }

    public void setAcknowledgedAt(Integer acknowledgedAt) {
        this.acknowledgedAt = acknowledgedAt;
    }

    public String getAcknowledgedBy() {
        return acknowledgedBy;
    }

    public void setAcknowledgedBy(String acknowledgedBy) {
        this.acknowledgedBy = acknowledgedBy;
    }

    public Integer getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(Integer resolvedAt) {
        this.resolvedAt = resolvedAt;
    }

    public String getResolvedBy() {
        return resolvedBy;
    }

    public void setResolvedBy(String resolvedBy) {
        this.resolvedBy = resolvedBy;
    }

    public Integer getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Integer updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<String> getAffectedResources() {
        return affectedResources;
    }

    public void setAffectedResources(List<String> affectedResources) {
        this.affectedResources = affectedResources;
    }

    public List<String> getBlastRadius() {
        return blastRadius;
    }

    public void setBlastRadius(List<String> blastRadius) {
        this.blastRadius = blastRadius;
    }

    public List<String> getRemediationSteps() {
        return remediationSteps;
    }

    public void setRemediationSteps(List<String> remediationSteps) {
        this.remediationSteps = remediationSteps;
    }

    public List<TimelineEntry> getTimeline() {
        return timeline;
    }

    public void setTimeline(List<TimelineEntry> timeline) {
        this.timeline = timeline;
    }

    public String getAcknowledgmentNotes() {
        return acknowledgmentNotes;
    }

    public void setAcknowledgmentNotes(String acknowledgmentNotes) {
        this.acknowledgmentNotes = acknowledgmentNotes;
    }

    public String getResolutionNotes() {
        return resolutionNotes;
    }

    public void setResolutionNotes(String resolutionNotes) {
        this.resolutionNotes = resolutionNotes;
    }

    public String getResolutionType() {
        return resolutionType;
    }

    public void setResolutionType(String resolutionType) {
        this.resolutionType = resolutionType;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    // Inner class for Identity Reference
    public static class IdentityReference {
        private ObjectId id;
        private String identityName;

        public IdentityReference() {}

        public IdentityReference(ObjectId id, String identityName) {
            this.id = id;
            this.identityName = identityName;
        }

        public ObjectId getId() {
            return id;
        }

        public void setId(ObjectId id) {
            this.id = id;
        }

        public String getHexId() {
            return this.id != null ? this.id.toHexString() : null;
        }

        public String getIdentityName() {
            return identityName;
        }

        public void setIdentityName(String identityName) {
            this.identityName = identityName;
        }
    }

    // Inner class for Timeline
    public static class TimelineEntry {
        private int timestamp;
        private String event;

        public TimelineEntry() {}

        public TimelineEntry(int timestamp, String event) {
            this.timestamp = timestamp;
            this.event = event;
        }

        public int getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(int timestamp) {
            this.timestamp = timestamp;
        }

        public String getEvent() {
            return event;
        }

        public void setEvent(String event) {
            this.event = event;
        }
    }
}
