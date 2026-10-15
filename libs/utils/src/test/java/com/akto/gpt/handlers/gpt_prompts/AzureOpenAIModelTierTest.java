package com.akto.gpt.handlers.gpt_prompts;

import com.akto.gpt.handlers.gpt_prompts.AzureOpenAIPromptHandler.ModelTier;
import org.json.JSONObject;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Handlers run on one of two Azure deployments: gpt-4o-mini (the default, unchanged behaviour) or
 * the cheap gpt-5-nano reasoning tier they explicitly opt into. This pins down which handler sits
 * where, and that each tier produces the request shape its model accepts. Pure payload building,
 * no network call.
 */
class AzureOpenAIModelTierTest {

    private static final String[] CLASSIC_KNOBS = {"max_tokens", "temperature", "top_p", "frequency_penalty", "presence_penalty"};

    // --- which handler runs on which tier ---

    @Test
    void cheapClosedFormHandlers_runOnNano() {
        AzureOpenAIPromptHandler[] nano = { new UserQueryTopicClassifier() };
        for (AzureOpenAIPromptHandler h : nano) {
            assertEquals(ModelTier.GPT_5_NANO, h.getModelTier(), h.getClass().getSimpleName());
        }
    }

    @Test
    void testEditorFamily_staysOnGpt4oMini() {
        AzureOpenAIPromptHandler[] testEditor = {
            new TestExecutorModifier(), new TestFilterModifier(), new TestValidatorModifier(),
            new McpMethodAnalyzer(), new MagicValidator()
        };
        for (AzureOpenAIPromptHandler h : testEditor) {
            assertEquals(ModelTier.GPT_4O_MINI, h.getModelTier(), h.getClass().getSimpleName());
        }
    }

    @Test
    void judgmentHeavyHandlers_stayOnGpt4oMiniUntilExplicitlyMoved() {
        AzureOpenAIPromptHandler[] stay = { new McpToolMaliciousnessAnalyzer(), new PromptHardeningTestHandler() };
        for (AzureOpenAIPromptHandler h : stay) {
            assertEquals(ModelTier.GPT_4O_MINI, h.getModelTier(), h.getClass().getSimpleName());
        }
    }

    // --- nano request shape ---

    @Test
    void nano_usesReasoningParamsAtMinimalEffortAndDropsClassicKnobs() throws Exception {
        for (AzureOpenAIPromptHandler h : new AzureOpenAIPromptHandler[]{ new UserQueryTopicClassifier() }) {
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
        assertEquals(3000, new UserQueryTopicClassifier().buildPayload("x").getInt("max_completion_tokens"));
    }

    @Test
    void nanoCaps_leaveRoomForTheMeasuredOutputs() {
        // Measured on gpt-5-nano at minimal effort: a batch of 10 topic labels ~760 output tokens.
        assertTrue(new UserQueryTopicClassifier().getMaxTokens() >= 3 * 760);
        // and the topic classifier must not fall back to the 10,000 default that Azure charges per call
        assertTrue(new UserQueryTopicClassifier().getMaxTokens() < PromptHandler.max_tokens);
    }

    @Test
    void nanoEffort_isMinimalForEveryNanoHandler() {
        assertEquals("minimal", new UserQueryTopicClassifier().getReasoningEffort());
    }

    // Every concrete handler in this package, found from the compiled classes so a new one is covered automatically.
    private static List<AzureOpenAIPromptHandler> allHandlers() throws Exception {
        File dir = new File(AzureOpenAIPromptHandler.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        Assumptions.assumeTrue(dir.isDirectory());
        List<AzureOpenAIPromptHandler> handlers = new ArrayList<>();
        File[] files = new File(dir, AzureOpenAIPromptHandler.class.getPackage().getName().replace('.', '/')).listFiles();
        for (File f : files) {
            if (!f.getName().endsWith(".class") || f.getName().contains("$")) continue;
            Class<?> c = Class.forName(AzureOpenAIPromptHandler.class.getPackage().getName() + "." + f.getName().replace(".class", ""));
            if (AzureOpenAIPromptHandler.class.isAssignableFrom(c) && !Modifier.isAbstract(c.getModifiers())) {
                handlers.add((AzureOpenAIPromptHandler) c.getDeclaredConstructor().newInstance());
            }
        }
        return handlers;
    }

    @Test
    void everyHandlerHasAWellFormedServiceKeyAndHandlersSharingOneAgreeOnTheirTier() throws Exception {
        List<AzureOpenAIPromptHandler> handlers = allHandlers();
        assertTrue(handlers.size() >= 8, "found " + handlers.size());
        Map<String, ModelTier> tierByService = new HashMap<>();
        for (AzureOpenAIPromptHandler h : handlers) {
            String name = h.getClass().getSimpleName();
            String key = h.getServiceKey();
            assertNotNull(key, name + " has no service key");
            assertTrue(key.matches("[a-z]+(-[a-z]+)*"), name + ": " + key);
            ModelTier prior = tierByService.putIfAbsent(key, h.getModelTier());
            if (prior != null) assertEquals(prior, h.getModelTier(), "handlers sharing '" + key + "' must share a tier");
        }
    }

    @Test
    void theTestEditorFamilyIsOneServiceStillOnGpt4oMini() {
        AzureOpenAIPromptHandler[] family = {
            new TestExecutorModifier(), new TestFilterModifier(), new TestValidatorModifier(), new McpMethodAnalyzer(), new MagicValidator()
        };
        for (AzureOpenAIPromptHandler h : family) {
            assertEquals("test-editor", h.getServiceKey(), h.getClass().getSimpleName());
            assertEquals(ModelTier.GPT_4O_MINI, h.getModelTier(), h.getClass().getSimpleName());
        }
        assertEquals("query-topic", new UserQueryTopicClassifier().getServiceKey());
    }

    @Test
    void aChainTargetGetsItsOwnTiersRequestShapeRegardlessOfTheHandlersTier() throws Exception {
        UserQueryTopicClassifier nanoHandler = new UserQueryTopicClassifier();
        JSONObject toMini = nanoHandler.buildPayload("x", ModelTier.GPT_4O_MINI);
        assertTrue(toMini.has("max_tokens"));
        assertTrue(toMini.has("temperature"));
        assertFalse(toMini.has("reasoning_effort"));

        McpToolMaliciousnessAnalyzer miniHandler = new McpToolMaliciousnessAnalyzer();
        JSONObject toNano = miniHandler.buildPayload("x", ModelTier.GPT_5_NANO);
        assertEquals("minimal", toNano.getString("reasoning_effort"));
        assertEquals(PromptHandler.max_tokens, toNano.getInt("max_completion_tokens"));
        assertFalse(toNano.has("temperature"));
    }

    @Test
    void aServiceChainWithNothingConfiguredFailsExactlyLikeAPlainAzureCall() {
        Assumptions.assumeTrue(System.getenv("AZURE_OPENAI_HOST") == null);
        LlmServiceChains.reset();
        RuntimeException e = assertThrows(RuntimeException.class, () -> new UserQueryTopicClassifier().call("x"));
        assertTrue(e.getMessage().contains("AZURE_OPENAI_HOST"));
    }

    @Test
    void nano_stillCarriesResponseFormatAndPrompt() throws Exception {
        JSONObject p = new UserQueryTopicClassifier().buildPayload("the prompt");
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
