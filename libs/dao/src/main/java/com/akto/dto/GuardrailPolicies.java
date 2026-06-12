package com.akto.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import com.akto.dao.ApiCollectionsDao;
import com.akto.dto.traffic.CollectionTags;
import com.akto.util.Constants;
import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;

import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
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

    // Step 7: Base Prompt Rule
    private BasePromptRule basePromptRule;

    // Step 7.5: Gibberish Detection - ML-based detection of nonsensical text
    private GibberishDetection gibberishDetection;
    // Step 8.5: Token Limit - enforce token limits on user inputs
    private TokenLimit tokenLimit;

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
    
    /** Policy-wide rule behaviour: {@code "block"}, {@code "warn"}, or {@code "alert"}. */
    private String behaviour;

    // Step 8: Review and Finish
    private boolean active;

    private CONTEXT_SOURCE contextSource;

    private SecretsDetection secretsDetection;
    private boolean applyToAllServers;

    // Blocked host/path list — block-only glob patterns matched against the request host+path.
    // Object-shaped so it can be extended later without a data migration.
    private List<BlockedHostEntry> blockedHosts;

    // Block personal / consumer accounts (non-enterprise email-type users).
    private boolean blockPersonalAccounts;

    // Modal config
    private ArrayList<ModelConfig> modelConfigs;

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
        if (selectedMcpServers != null && !selectedMcpServers.isEmpty()) {
            return selectedMcpServers.stream()
                    .map(serverId -> new SelectedServer(serverId, serverId))
                    .collect(java.util.stream.Collectors.toList());
        }
        if (contextSource != null && contextSource != CONTEXT_SOURCE.AGENTIC) {
            return new ArrayList<>();
        }
        if (!applyToAllServers) {
            return new ArrayList<>();
        }
        return fetchApplicableServersByContext();
    }

    public List<SelectedServer> getEffectiveSelectedAgentServers() {
        if (selectedAgentServersV2 != null && !selectedAgentServersV2.isEmpty()) {
            return selectedAgentServersV2;
        }
        if (selectedAgentServers != null && !selectedAgentServers.isEmpty()) {
            return selectedAgentServers.stream()
                    .map(serverId -> new SelectedServer(serverId, serverId))
                    .collect(java.util.stream.Collectors.toList());
        }
        if (contextSource != null && contextSource != CONTEXT_SOURCE.AGENTIC) {
            return new ArrayList<>();
        }
        if (!applyToAllServers) {
            return new ArrayList<>();
        }
        return fetchApplicableServersByContext();
    }

    // filters collections by the policy's contextSource tag (key="source", value=contextSource.name())
    // excludes Atlas/endpoint collections (source=ENDPOINT); returns inline-only when behaviour is "block"
    private List<SelectedServer> fetchApplicableServersByContext() {
        if (contextSource == null) return new ArrayList<>();
        boolean isBlock = "block".equals(this.behaviour);
        Bson filter = Filters.and(
                Filters.elemMatch(ApiCollection.TAGS_STRING, Filters.and(
                        Filters.eq(CollectionTags.KEY_NAME, Constants.AKTO_COLLECTION_CONTEXT_TAG_KEY),
                        Filters.eq(CollectionTags.VALUE, contextSource.name())
                )),
                Filters.not(Filters.elemMatch(ApiCollection.TAGS_STRING, Filters.and(
                        Filters.eq(CollectionTags.KEY_NAME, Constants.AKTO_COLLECTION_CONTEXT_TAG_KEY),
                        Filters.eq(CollectionTags.VALUE, CONTEXT_SOURCE.ENDPOINT.name())
                )))
        );
        if (isBlock) {
            filter = Filters.and(filter, Filters.elemMatch(ApiCollection.TAGS_STRING, Filters.and(
                    Filters.eq(CollectionTags.KEY_NAME, Constants.AKTO_GUARDRAIL_MODE),
                    Filters.eq(CollectionTags.VALUE, Constants.AKTO_GUARDRAIL_MODE_INLINE)
            )));
        }
        return ApiCollectionsDao.instance.findAll(filter, Projections.include(ApiCollection.ID, "name"))
                .stream()
                .map(c -> new SelectedServer(String.valueOf(c.getId()), c.getName()))
                .collect(java.util.stream.Collectors.toList());
    }

    public GuardrailPolicies(String name, String description, String blockedMessage, String severity, int createdTimestamp,
                           int updatedTimestamp, String createdBy, String updatedBy, String selectedCollection,
                           String selectedModel, List<DeniedTopic> deniedTopics, List<PiiType> piiTypes,
                           List<String> regexPatterns, List<RegexPattern> regexPatternsV2, Map<String, Object> contentFiltering,
                           LLMRule llmRule, GibberishDetection gibberishDetection, TokenLimit tokenLimit,
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
        this.gibberishDetection = gibberishDetection;
        this.tokenLimit = tokenLimit;
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
        /** Minimum matches for this data type in the prompt (inclusive) to trigger; e.g. 20 means fire when 20+ of this type are present. */
        private int minMatchCount = 1;

        public PiiType(String type, String behavior, int minMatchCount) {
            this.type = type;
            this.behavior = behavior;
            this.minMatchCount = minMatchCount >= 1 ? minMatchCount : 1;
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

    /**
     * A single blocked host entry (block-only). {@code pattern} is a glob matched against the
     * request's {@code host + path}, where {@code *} matches any sequence (e.g. "chatgpt.com/*",
     * "*.deepseek.com/*"). Kept as an object so it can be extended later without a migration.
     */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class BlockedHostEntry {
        private String pattern;

        public BlockedHostEntry(String pattern) {
            this.pattern = pattern;
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

    @Getter
    @Setter
    @NoArgsConstructor
    public static class BasePromptRule {
        private boolean enabled;
        private double confidenceScore;

        public BasePromptRule(boolean enabled, double confidenceScore) {
            this.enabled = enabled;
            this.confidenceScore = confidenceScore;
        }
    }


    @Getter
    @Setter
    @NoArgsConstructor
    public static class GibberishDetection {
        private boolean enabled;
        private double confidenceScore;

        public GibberishDetection(boolean enabled, double confidenceScore) {
            this.enabled = enabled;
            this.confidenceScore = confidenceScore;
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class TokenLimit {
        private boolean enabled;
        private int threshold;

        public TokenLimit(boolean enabled, int threshold) {
            this.enabled = enabled;
            this.threshold = threshold;
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class SecretsDetection {
        private boolean enabled;
        private double confidenceScore;

        public SecretsDetection(boolean enabled, double confidenceScore) {
            this.enabled = enabled;
            this.confidenceScore = confidenceScore;
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class SentimentDetection {
        private boolean enabled;
        private double confidenceScore;

        public SentimentDetection(boolean enabled, double confidenceScore) {
            this.enabled = enabled;
            this.confidenceScore = confidenceScore;
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class TokenLimitDetection {
        private boolean enabled;
        private double confidenceScore;

        public TokenLimitDetection(boolean enabled, double confidenceScore) {
            this.enabled = enabled;
            this.confidenceScore = confidenceScore;
        }
    }

    public enum ModelRole {
        FAST_THREAT_FILTER,     
        FAST_FALLBACK_SAFE_FILTER,  
        FINAL_ARBITER    
    }
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ModelConfig {
        private String provider;
        private String model;
        private String baseUrl;
        private int timeoutMs;
        private boolean strictBlock;
        private boolean strictAllow;
        private ModelRole modelRole;
        private String attackType;
        private double safeDecisionThreshold;
    }

}