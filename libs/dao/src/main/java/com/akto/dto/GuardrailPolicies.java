package com.akto.dto;

import java.util.List;
import java.util.Map;

import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.bson.types.ObjectId;

import lombok.NoArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class GuardrailPolicies {

    private ObjectId id;

    @BsonIgnore
    private String hexId;
    
    private String name;
    private String description;
    private String blockedMessage;
    private String severity;
    private int createdTimestamp;
    private int updatedTimestamp;
    private String createdBy;
    
    // Step 2: System Selection
    private String selectedCollection;
    private String selectedModel;
    
    // Step 3: Denied Topics
    private List<DeniedTopic> deniedTopics;
    
    // Step 4: PII Detection
    private List<PiiType> piiTypes;
    
    // Step 4.5: Regex Patterns
    private List<String> regexPatterns;
    
    // Step 5: Content Filtering
    private Map<String, Object> contentFiltering;
    
    // Step 6: Server and application settings
    private List<String> selectedMcpServers;
    private List<String> selectedAgentServers;
    private boolean applyOnResponse;
    private boolean applyOnRequest;
    
    // Step 7: Review and Finish
    private boolean active;

    public String getHexId() {
        if (this.id != null) {
            return this.id.toHexString();
        }
        return null;
    }

    public GuardrailPolicies(String name, String description, String blockedMessage, String severity, int createdTimestamp, 
                           int updatedTimestamp, String createdBy, String selectedCollection, 
                           String selectedModel, List<DeniedTopic> deniedTopics, List<PiiType> piiTypes,
                           List<String> regexPatterns, Map<String, Object> contentFiltering, List<String> selectedMcpServers,
                           List<String> selectedAgentServers, boolean applyOnResponse, boolean applyOnRequest,
                           boolean active) {
        this.name = name;
        this.description = description;
        this.blockedMessage = blockedMessage;
        this.severity = severity;
        this.createdTimestamp = createdTimestamp;
        this.updatedTimestamp = updatedTimestamp;
        this.createdBy = createdBy;
        this.selectedCollection = selectedCollection;
        this.selectedModel = selectedModel;
        this.deniedTopics = deniedTopics;
        this.piiTypes = piiTypes;
        this.regexPatterns = regexPatterns;
        this.contentFiltering = contentFiltering;
        this.selectedMcpServers = selectedMcpServers;
        this.selectedAgentServers = selectedAgentServers;
        this.applyOnResponse = applyOnResponse;
        this.applyOnRequest = applyOnRequest;
        this.active = active;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class DeniedTopic {
        private String topic;
        private String description;
        private List<String> samplePhrases;

        public DeniedTopic(String topic, String description, List<String> samplePhrases) {
            this.topic = topic;
            this.description = description;
            this.samplePhrases = samplePhrases;
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class PiiType {
        private String type;
        private String behavior; // "Block" or "Mask"

        public PiiType(String type, String behavior) {
            this.type = type;
            this.behavior = behavior;
        }
    }
}