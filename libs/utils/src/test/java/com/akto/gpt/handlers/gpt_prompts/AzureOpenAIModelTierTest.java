package com.akto.gpt.handlers.gpt_prompts;

import com.akto.gpt.handlers.gpt_prompts.AzureOpenAIPromptHandler.ModelTier;
import com.mongodb.BasicDBObject;
import org.json.JSONObject;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Only the handlers that opt into a per-service chain (see LlmServiceChains, via getServiceKey())
 * run on an explicit ModelTier and get the raw-Azure request shape asserted here. Every other
 * AzureOpenAIPromptHandler subclass in this package predates this feature and keeps resolving its
 * model through AgentModelDao / LLMProviderClient untouched — that legacy path is out of scope for
 * this test. Pure payload building, no network call.
 */
class AzureOpenAIModelTierTest {

    private static final String[] CLASSIC_KNOBS = {"max_tokens", "temperature", "top_p", "frequency_penalty", "presence_penalty"};

    private static AzureOpenAIPromptHandler[] chainOptedHandlers() {
        return new AzureOpenAIPromptHandler[]{
            new McpToolMaliciousnessAnalyzer(), new PromptHardeningTestHandler(),
            new TestExecutorModifier(), new TestFilterModifier(), new TestValidatorModifier(),
            new McpMethodAnalyzer(), new MagicValidator(),
            new ToolCapabilityClassifier(), new AgentDomainClassifier(), new GuardrailSuggestionClassifier(),
            new AgentBaseRiskScoreAnalyzer()
        };
    }

    // --- which handler runs on which tier ---

    @Test
    void cheapClosedFormHandlersRunOnNano() {
        AzureOpenAIPromptHandler[] nano = {
            new ToolCapabilityClassifier(), new AgentDomainClassifier(), new GuardrailSuggestionClassifier(),
            new AgentBaseRiskScoreAnalyzer()
        };
        for (AzureOpenAIPromptHandler h : nano) {
            assertEquals(ModelTier.GPT_5_NANO, h.getModelTier(), h.getClass().getSimpleName());
        }
    }

    @Test
    void judgmentHeavyAndTestEditorHandlersStayOnGpt4oMini() {
        AzureOpenAIPromptHandler[] mini = {
            new McpToolMaliciousnessAnalyzer(), new PromptHardeningTestHandler(),
            new TestExecutorModifier(), new TestFilterModifier(), new TestValidatorModifier(),
            new McpMethodAnalyzer(), new MagicValidator()
        };
        for (AzureOpenAIPromptHandler h : mini) {
            assertEquals(ModelTier.GPT_4O_MINI, h.getModelTier(), h.getClass().getSimpleName());
        }
    }

    @Test
    void chainOptedHandlersHaveAWellFormedServiceKeyAndHandlersSharingOneAgreeOnTheirTier() {
        Map<String, ModelTier> tierByService = new HashMap<>();
        for (AzureOpenAIPromptHandler h : chainOptedHandlers()) {
            String name = h.getClass().getSimpleName();
            String key = h.getServiceKey();
            assertNotNull(key, name + " has no service key");
            assertTrue(key.matches("[a-z]+(-[a-z]+)*"), name + ": " + key);
            ModelTier prior = tierByService.putIfAbsent(key, h.getModelTier());
            if (prior != null) assertEquals(prior, h.getModelTier(), "handlers sharing '" + key + "' must share a tier");
        }
    }

    @Test
    void theTestEditorFamilyIsOneServiceOnGpt4oMini() {
        AzureOpenAIPromptHandler[] family = {
            new TestExecutorModifier(), new TestFilterModifier(), new TestValidatorModifier(), new McpMethodAnalyzer(), new MagicValidator()
        };
        for (AzureOpenAIPromptHandler h : family) {
            assertEquals("test-editor", h.getServiceKey(), h.getClass().getSimpleName());
            assertEquals(ModelTier.GPT_4O_MINI, h.getModelTier(), h.getClass().getSimpleName());
        }
    }

    @Test
    void eachNanoClassifierHasItsOwnServiceKey() {
        assertEquals("tool-capability", new ToolCapabilityClassifier().getServiceKey());
        assertEquals("agent-domain", new AgentDomainClassifier().getServiceKey());
        assertEquals("guardrail-suggestion", new GuardrailSuggestionClassifier().getServiceKey());
        assertEquals("agent-base-risk-score", new AgentBaseRiskScoreAnalyzer().getServiceKey());
    }

    @Test
    void judgmentHeavyHandlersHaveTheirOwnServiceKeysStillOnGpt4oMini() {
        assertEquals("mcp-tool-maliciousness", new McpToolMaliciousnessAnalyzer().getServiceKey());
        assertEquals("prompt-hardening", new PromptHardeningTestHandler().getServiceKey());
    }

    @Test
    void handlersThatPredateThisFeatureHaveNoServiceKeyAndAreUnaffected() {
        AzureOpenAIPromptHandler[] legacy = {
            new EndpointDescriptionBatchPromptHandler(), new CollectionDescriptionPromptHandler(),
            new PlatformOnlyDescriptionPromptHandler()
        };
        for (AzureOpenAIPromptHandler h : legacy) {
            assertNull(h.getServiceKey(), h.getClass().getSimpleName());
        }
    }

    // --- nano request shape ---

    @Test
    void nano_usesReasoningParamsAtMinimalEffortAndDropsClassicKnobs() throws Exception {
        for (AzureOpenAIPromptHandler h : new AzureOpenAIPromptHandler[]{
                new ToolCapabilityClassifier(), new AgentDomainClassifier(), new GuardrailSuggestionClassifier(),
                new AgentBaseRiskScoreAnalyzer()}) {
            JSONObject p = h.buildPayload("x");
            assertEquals("minimal", p.getString("reasoning_effort"), h.getClass().getSimpleName());
            assertTrue(p.has("max_completion_tokens"), h.getClass().getSimpleName());
            for (String knob : CLASSIC_KNOBS) {
                assertFalse(p.has(knob), h.getClass().getSimpleName() + " must not send " + knob);
            }
        }
    }

    @Test
    void nano_sendsEachHandlersOwnCapAsMaxCompletionTokens() throws Exception {
        // No reasoning allowance is added: to give a handler more room, raise its getMaxTokens().
        assertEquals(500, new ToolCapabilityClassifier().buildPayload("x").getInt("max_completion_tokens"));
        assertEquals(300, new AgentDomainClassifier().buildPayload("x").getInt("max_completion_tokens"));
        assertEquals(2000, new GuardrailSuggestionClassifier().buildPayload("x").getInt("max_completion_tokens"));
        assertEquals(1000, new AgentBaseRiskScoreAnalyzer().buildPayload("x").getInt("max_completion_tokens"));
    }

    @Test
    void nanoEffort_isMinimalForEveryNanoHandler() {
        assertEquals("minimal", new ToolCapabilityClassifier().getReasoningEffort());
        assertEquals("minimal", new AgentDomainClassifier().getReasoningEffort());
        assertEquals("minimal", new GuardrailSuggestionClassifier().getReasoningEffort());
        assertEquals("minimal", new AgentBaseRiskScoreAnalyzer().getReasoningEffort());
    }

    @Test
    void aChainTargetGetsItsOwnTiersRequestShapeRegardlessOfTheHandlersTier() throws Exception {
        McpToolMaliciousnessAnalyzer miniHandler = new McpToolMaliciousnessAnalyzer();
        JSONObject toMini = miniHandler.buildPayload("x", ModelTier.GPT_4O_MINI);
        assertTrue(toMini.has("max_tokens"));
        assertTrue(toMini.has("temperature"));
        assertFalse(toMini.has("reasoning_effort"));

        JSONObject toNano = miniHandler.buildPayload("x", ModelTier.GPT_5_NANO);
        assertEquals("minimal", toNano.getString("reasoning_effort"));
        assertEquals(PromptHandler.max_tokens, toNano.getInt("max_completion_tokens"));
        assertFalse(toNano.has("temperature"));
    }

    @Test
    void aServiceChainWithNothingConfiguredFailsExactlyLikeAPlainAzureCall() {
        Assumptions.assumeTrue(System.getenv("AZURE_OPENAI_HOST") == null);
        LlmServiceChains.reset();
        RuntimeException e = assertThrows(RuntimeException.class, () -> new McpToolMaliciousnessAnalyzer().call("x"));
        assertTrue(e.getMessage().contains("AZURE_OPENAI_HOST"));
    }

    @Test
    void anUnconfiguredInstallStillReportsAnErrorInsteadOfClassifyingEverythingSafe() {
        // On-prem without Azure env: a swallowed failure would reach processResponse as NOT_FOUND,
        // which ToolCapabilityClassifier reads as an error, not SAFE.
        Assumptions.assumeTrue(System.getenv("AZURE_OPENAI_HOST") == null);
        LlmServiceChains.reset();
        BasicDBObject result = new ToolCapabilityClassifier().handle(new BasicDBObject(ToolCapabilityClassifier.TOOL_NAME, "delete_everything"));
        assertTrue(result.containsField("error"), result.toString());
        assertFalse(result.containsField(ToolCapabilityClassifier.CAPABILITY));
    }

    @Test
    void nano_stillCarriesResponseFormatAndPrompt() throws Exception {
        JSONObject p = new ToolCapabilityClassifier().buildPayload("the prompt");
        assertNotNull(p.optJSONObject("response_format"));
        assertEquals("the prompt", p.getJSONArray("messages").getJSONObject(0).getString("content"));
    }

    // --- gpt-4o-mini request shape (unchanged) ---

    @Test
    void gpt4oMini_keepsTheClassicRequestShape() throws Exception {
        // If AZURE_OPENAI_DEPLOYMENT happens to name a gpt-5/o-series model the reasoning shape is
        // (deliberately) used for it too, so only assert the classic shape for a classic name.
        String configured = System.getenv("AZURE_OPENAI_DEPLOYMENT");
        Assumptions.assumeTrue(configured == null || !configured.toLowerCase().matches("^(gpt-5|o1|o3|o4).*"));

        JSONObject p = new McpToolMaliciousnessAnalyzer().buildPayload("x");
        assertEquals(PromptHandler.max_tokens, p.getInt("max_tokens"));
        assertTrue(p.has("temperature"));
        assertTrue(p.has("top_p"));
        assertFalse(p.has("reasoning_effort"));
        assertFalse(p.has("max_completion_tokens"));
    }

    // --- env-driven deployment names ---

    @Test
    void resolveDeployment_envWinsWhenSetElseDefault() {
        assertEquals("gpt-5-nano", AzureOpenAIPromptHandler.resolveDeployment(null, "gpt-5-nano"));
        assertEquals("gpt-5-nano", AzureOpenAIPromptHandler.resolveDeployment("", "gpt-5-nano"));
        assertEquals("gpt-5-nano", AzureOpenAIPromptHandler.resolveDeployment("   ", "gpt-5-nano"));
        assertEquals("my-nano", AzureOpenAIPromptHandler.resolveDeployment(" my-nano ", "gpt-5-nano"));
        assertNull(AzureOpenAIPromptHandler.resolveDeployment(null, null)); // 4o-mini tier: no default, env required
    }

    @Test
    void resolveApiVersion_tierVarThenSharedVarThenDefault() {
        String def = AzureOpenAIPromptHandler.DEFAULT_API_VERSION;
        assertEquals("2025-08-01-preview", AzureOpenAIPromptHandler.resolveApiVersion("2025-08-01-preview", "2024-06-01"));
        assertEquals("2024-06-01", AzureOpenAIPromptHandler.resolveApiVersion(null, "2024-06-01"));
        assertEquals("2024-06-01", AzureOpenAIPromptHandler.resolveApiVersion("  ", " 2024-06-01 "));
        assertEquals(def, AzureOpenAIPromptHandler.resolveApiVersion(null, null));
        assertEquals(def, AzureOpenAIPromptHandler.resolveApiVersion("", ""));
    }

    @Test
    void eachTierIsCalledWithItsOwnDeploymentAndApiVersion() {
        String url = AzureOpenAIPromptHandler.chatCompletionsUrl("https://h.example", ModelTier.GPT_5_NANO);
        assertEquals("https://h.example/openai/deployments/" + ModelTier.GPT_5_NANO.deployment
                + "/chat/completions?api-version=" + ModelTier.GPT_5_NANO.apiVersion, url);

        // Setting only the nano var must not move the 4o-mini tier (and vice versa): with none of the
        // version vars set both resolve to the same default, which is the pre-tier behaviour.
        String shared = System.getenv("AZURE_OPENAI_API_VERSION");
        String nanoVar = System.getenv("AZURE_OPENAI_5_NANO_API_VERSION");
        Assumptions.assumeTrue((shared == null || shared.trim().isEmpty()) && (nanoVar == null || nanoVar.trim().isEmpty()));
        assertEquals(AzureOpenAIPromptHandler.DEFAULT_API_VERSION, ModelTier.GPT_4O_MINI.apiVersion);
        assertEquals(AzureOpenAIPromptHandler.DEFAULT_API_VERSION, ModelTier.GPT_5_NANO.apiVersion);
    }

    @Test
    void nanoDeployment_defaultsToGpt5NanoWhenEnvUnset() {
        String override = System.getenv("AZURE_OPENAI_5_NANO_DEPLOYMENT");
        Assumptions.assumeTrue(override == null || override.trim().isEmpty());
        assertEquals("gpt-5-nano", ModelTier.GPT_5_NANO.deployment);
    }
}
