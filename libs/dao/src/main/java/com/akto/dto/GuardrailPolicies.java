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
    private String updatedBy;
    
    // Step 2: System Selection
    private String selectedCollection;
    private String selectedModel;
    
    // Step 3: Denied Topics
    private List<DeniedTopic> deniedTopics;
    
    // Step 4: PII Detection
    private List<PiiType> piiTypes;
    
    // Step 4.5: Regex Patterns (old format - backward compatibility)
    private List<String> regexPatterns;
    
    // Step 4.5: Enhanced Regex Patterns (new format with behavior)
    private List<RegexPattern> regexPatternsV2;
    
    // Step 5: Content Filtering
    private Map<String, Object> contentFiltering;

    private LLMRule llmRule;

    // Step 7: Server and application settings (old format - backward compatibility)
    private List<String> selectedMcpServers;
    private List<String> selectedAgentServers;
    
    // Step 6: Enhanced Server settings (new format with ID and name)
    private List<SelectedServer> selectedMcpServersV2;
    private List<SelectedServer> selectedAgentServersV2;
    private boolean applyOnResponse;
    private boolean applyOnRequest;
    
    // Step 7: URL and Confidence Score
    private String url;
    private double confidenceScore;
    
    // Step 8: Review and Finish
    private boolean active;

    public String getHexId() {
        if (this.id != null) {
            return this.id.toHexString();
        }
        return null;
    }

    // Helper methods for backward compatibility - prefer V2 fields if available, fallback to old fields
    public List<RegexPattern> getEffectiveRegexPatterns() {
        if (regexPatternsV2 != null && !regexPatternsV2.isEmpty()) {
            return regexPatternsV2;
        }
        // Convert old format to new format for compatibility
        if (regexPatterns != null && !regexPatterns.isEmpty()) {
            return regexPatterns.stream()
                    .map(pattern -> new RegexPattern(pattern, "block")) // Default behavior
                    .collect(java.util.stream.Collectors.toList());
        }
        return new java.util.ArrayList<>();
    }

    public List<SelectedServer> getEffectiveSelectedMcpServers() {
        if (selectedMcpServersV2 != null && !selectedMcpServersV2.isEmpty()) {
            return selectedMcpServersV2;
        }
        // Convert old format to new format for compatibility
        if (selectedMcpServers != null && !selectedMcpServers.isEmpty()) {
            return selectedMcpServers.stream()
                    .map(serverId -> new SelectedServer(serverId, serverId)) // ID as name for old data
                    .collect(java.util.stream.Collectors.toList());
        }
        return new java.util.ArrayList<>();
    }

    public List<SelectedServer> getEffectiveSelectedAgentServers() {
        if (selectedAgentServersV2 != null && !selectedAgentServersV2.isEmpty()) {
            return selectedAgentServersV2;
        }
        // Convert old format to new format for compatibility
        if (selectedAgentServers != null && !selectedAgentServers.isEmpty()) {
            return selectedAgentServers.stream()
                    .map(serverId -> new SelectedServer(serverId, serverId)) // ID as name for old data
                    .collect(java.util.stream.Collectors.toList());
        }
        return new java.util.ArrayList<>();
    }

    public GuardrailPolicies(String name, String description, String blockedMessage, String severity, int createdTimestamp,
                           int updatedTimestamp, String createdBy, String updatedBy, String selectedCollection,
                           String selectedModel, List<DeniedTopic> deniedTopics, List<PiiType> piiTypes,
                           List<String> regexPatterns, List<RegexPattern> regexPatternsV2, Map<String, Object> contentFiltering,
                           LLMRule llmRule,
                           List<String> selectedMcpServers, List<String> selectedAgentServers,
                           List<SelectedServer> selectedMcpServersV2, List<SelectedServer> selectedAgentServersV2,
                           boolean applyOnResponse, boolean applyOnRequest, String url, double confidenceScore, boolean active) {
        this.name = name;
        this.description = description;
        this.blockedMessage = blockedMessage;
        this.severity = severity;
        this.createdTimestamp = createdTimestamp;
        this.updatedTimestamp = updatedTimestamp;
        this.createdBy = createdBy;
        this.updatedBy = updatedBy;
        this.selectedCollection = selectedCollection;
        this.selectedModel = selectedModel;
        this.deniedTopics = deniedTopics;
        this.piiTypes = piiTypes;
        this.regexPatterns = regexPatterns;
        this.regexPatternsV2 = regexPatternsV2;
        this.contentFiltering = contentFiltering;
        this.llmRule = llmRule;
        this.selectedMcpServers = selectedMcpServers;
        this.selectedAgentServers = selectedAgentServers;
        this.selectedMcpServersV2 = selectedMcpServersV2;
        this.selectedAgentServersV2 = selectedAgentServersV2;
        this.applyOnResponse = applyOnResponse;
        this.applyOnRequest = applyOnRequest;
        this.url = url;
        this.confidenceScore = confidenceScore;
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

    @Getter
    @Setter
    @NoArgsConstructor
    public static class RegexPattern {
        private String pattern;
        private String behavior; // "Block" or "Mask"

        public RegexPattern(String pattern, String behavior) {
            this.pattern = pattern;
            this.behavior = behavior;
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class SelectedServer {
        private String id;
        private String name;

        public SelectedServer(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class LLMRule {
        private boolean enabled;
        private String userPrompt;
        private double confidenceScore;

        public LLMRule(boolean enabled, String userPrompt, double confidenceScore) {
            this.enabled = enabled;
            this.userPrompt = userPrompt;
            this.confidenceScore = confidenceScore;
        }
    }
}