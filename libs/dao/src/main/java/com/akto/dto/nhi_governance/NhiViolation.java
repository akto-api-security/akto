package com.akto.dto.nhi_governance;

import java.util.List;
import java.util.Map;
import org.bson.types.ObjectId;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class NhiViolation {
    public static final String COLLECTION_NAME = "nhi_violations";

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
    public static final String POLICY_IDS = "policyIds";

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
    private List<String> policyIds;

    public NhiViolation(String violationType, List<IdentityReference> identities, String agentName, String severity) {
        this.violationType = violationType;
        this.identities = identities;
        this.agentName = agentName;
        this.severity = severity;
    }

    public String getHexId() {
        return this.id != null ? this.id.toHexString() : null;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class IdentityReference {
        private ObjectId id;
        private String identityName;

        public IdentityReference(ObjectId id, String identityName) {
            this.id = id;
            this.identityName = identityName;
        }

        public String getHexId() {
            return this.id != null ? this.id.toHexString() : null;
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class TimelineEntry {
        private int timestamp;
        private String event;

        public TimelineEntry(int timestamp, String event) {
            this.timestamp = timestamp;
            this.event = event;
        }
    }
}
