package com.akto.dto;

import com.akto.dto.GuardrailPolicies.AnonymizeDetection;
import com.akto.dto.GuardrailPolicies.BanCodeDetection;
import com.akto.dto.GuardrailPolicies.BasePromptRule;
import com.akto.dto.GuardrailPolicies.DeniedTopic;
import com.akto.dto.GuardrailPolicies.GibberishDetection;
import com.akto.dto.GuardrailPolicies.LLMRule;
import com.akto.dto.GuardrailPolicies.PiiType;
import com.akto.dto.GuardrailPolicies.RegexPattern;
import com.akto.dto.GuardrailPolicies.SecretsDetection;
import com.akto.dto.GuardrailPolicies.SentimentDetection;
import com.akto.dto.GuardrailPolicies.TokenLimitDetection;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GuardrailCompliancePreset {

    // Unique key used as document _id (e.g. "GDPR", "HIPAA")
    private String key;

    private String name;
    private String description;
    private String severity;
    private String behaviour;
    private String blockedMessage;
    private boolean applyOnRequest;
    private boolean applyOnResponse;

    // Detection configs — same inner-class types as GuardrailPolicies
    private List<PiiType> piiTypes;
    private List<RegexPattern> regexPatternsV2;
    private List<DeniedTopic> deniedTopics;

    // contentFiltering holds promptAttacks / harmfulCategories / code as a generic map
    // to match the existing GuardrailPolicies storage shape
    private Map<String, Object> contentFiltering;

    private SecretsDetection secretsDetection;
    private AnonymizeDetection anonymizeDetection;
    private GibberishDetection gibberishDetection;
    private BanCodeDetection banCodeDetection;
    private SentimentDetection sentimentDetection;
    private TokenLimitDetection tokenLimitDetection;
    private BasePromptRule basePromptRule;
    private LLMRule llmRule;

    // Sync metadata — used for idempotent upserts
    private String hash;
    private long syncedAt;
}
