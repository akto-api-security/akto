package com.akto.action.llm;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ModelTierTest {

    @Test
    public void resolveDeployment_prefersEnvValueWhenSet() {
        assertEquals("custom-deployment", ModelTier.resolveDeployment("custom-deployment", "default-deployment"));
    }

    @Test
    public void resolveDeployment_fallsBackToDefaultWhenBlank() {
        assertEquals("default-deployment", ModelTier.resolveDeployment("  ", "default-deployment"));
        assertEquals("default-deployment", ModelTier.resolveDeployment(null, "default-deployment"));
    }

    @Test
    public void resolveApiVersion_prefersTierSpecificEnvValue() {
        assertEquals("2025-01-01", ModelTier.resolveApiVersion("2025-01-01", "2024-01-01"));
    }

    @Test
    public void resolveApiVersion_fallsBackToSharedEnvValue() {
        assertEquals("2024-01-01", ModelTier.resolveApiVersion(null, "2024-01-01"));
        assertEquals("2024-01-01", ModelTier.resolveApiVersion("  ", "2024-01-01"));
    }

    @Test
    public void resolveApiVersion_fallsBackToDefaultWhenNothingSet() {
        assertEquals(ModelTier.DEFAULT_API_VERSION, ModelTier.resolveApiVersion(null, null));
    }

    @Test
    public void gpt5Nano_isAlwaysReasoning() {
        assertTrue(ModelTier.GPT_5_NANO.isReasoningModel());
    }

    @Test
    public void gpt4oMini_isNotReasoningWhenDeploymentUnset() {
        // No AZURE_OPENAI_DEPLOYMENT in the test environment, so the deployment name is null and the
        // gpt-5/o-series name heuristic has nothing to match against.
        if (ModelTier.GPT_4O_MINI.deployment == null) {
            assertFalse(ModelTier.GPT_4O_MINI.isReasoningModel());
        }
    }

    @Test
    public void buildAzureOpenAIUrl_requiresHost() {
        try {
            ModelTier.GPT_5_NANO.buildAzureOpenAIUrl("");
            fail("expected RuntimeException for blank host");
        } catch (RuntimeException expected) {
            assertTrue(expected.getMessage().contains("AZURE_OPENAI_HOST"));
        }
    }

    @Test
    public void buildAzureOpenAIUrl_buildsChatCompletionsUrl() {
        String url = ModelTier.GPT_5_NANO.buildAzureOpenAIUrl("https://my-resource.openai.azure.com");
        assertTrue(url.startsWith("https://my-resource.openai.azure.com/openai/deployments/"));
        assertTrue(url.contains(ModelTier.GPT_5_NANO.deployment));
        assertTrue(url.contains("api-version=" + ModelTier.GPT_5_NANO.apiVersion));
    }
}
