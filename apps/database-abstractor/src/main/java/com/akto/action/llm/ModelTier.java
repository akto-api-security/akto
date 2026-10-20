package com.akto.action.llm;

import java.util.Locale;

/**
 * Which Azure deployment (model) a default (non-override) LLM call runs on, and the api-version it
 * is called with. Both come from the tier's own env vars, so repointing, renaming or re-versioning a
 * model never needs a code change. Host and api key are shared (AZURE_OPENAI_HOST/AZURE_OPENAI_API_KEY),
 * so each deployment must exist on that same Azure resource.
 */
public enum ModelTier {

    /** gpt-4o-mini, a classic (non-reasoning) model. The long-standing AZURE_OPENAI_DEPLOYMENT /
     *  AZURE_OPENAI_API_VERSION vars; the deployment is required, no default — this is what every
     *  caller ran on before tiers existed. */
    GPT_4O_MINI("AZURE_OPENAI_DEPLOYMENT", null, "AZURE_OPENAI_API_VERSION", false),
    /** gpt-5-nano, a reasoning model, always called at "minimal" reasoning effort. Its api-version is
     *  its own var, falling back to the shared AZURE_OPENAI_API_VERSION when unset. */
    GPT_5_NANO("AZURE_OPENAI_5_NANO_DEPLOYMENT", "gpt-5-nano", "AZURE_OPENAI_5_NANO_API_VERSION", true);

    static final String DEFAULT_API_VERSION = "2024-04-01-preview";
    private static final String AZURE_API_ENDPOINT = "%s/openai/deployments/%s/chat/completions?api-version=%s";

    public final String deployment;
    public final String apiVersion;
    public final boolean reasoning;

    ModelTier(String deploymentEnvVar, String defaultDeployment, String apiVersionEnvVar, boolean reasoning) {
        this.deployment = resolveDeployment(System.getenv(deploymentEnvVar), defaultDeployment);
        this.apiVersion = resolveApiVersion(System.getenv(apiVersionEnvVar), System.getenv("AZURE_OPENAI_API_VERSION"));
        this.reasoning = reasoning;
    }

    /** Env value wins when set and non-blank, else the tier's own default (if any). */
    static String resolveDeployment(String envValue, String defaultDeployment) {
        return envValue != null && !envValue.trim().isEmpty() ? envValue.trim() : defaultDeployment;
    }

    /** The tier's own api-version when set, else the shared AZURE_OPENAI_API_VERSION, else the default. */
    static String resolveApiVersion(String tierEnvValue, String sharedEnvValue) {
        if (tierEnvValue != null && !tierEnvValue.trim().isEmpty()) return tierEnvValue.trim();
        if (sharedEnvValue != null && !sharedEnvValue.trim().isEmpty()) return sharedEnvValue.trim();
        return DEFAULT_API_VERSION;
    }

    public String buildAzureOpenAIUrl(String azureOpenAIHost) {
        if (azureOpenAIHost == null || azureOpenAIHost.isEmpty()) {
            throw new RuntimeException("AZURE_OPENAI_HOST environment variable is not set");
        }
        if (deployment == null || deployment.isEmpty()) {
            throw new RuntimeException("AZURE_OPENAI_DEPLOYMENT environment variable is not set");
        }
        return String.format(AZURE_API_ENDPOINT, azureOpenAIHost, deployment, apiVersion);
    }

    // GPT-5 / o-series ("reasoning") deployments reject the classic sampling knobs outright and spend
    // part of their token budget on invisible reasoning tokens before writing the visible answer.
    public boolean isReasoningModel() {
        if (reasoning) return true;
        // AZURE_OPENAI_DEPLOYMENT has also been pointed at gpt-5 / o-series names before; keep giving
        // those the reasoning request shape.
        if (deployment == null) return false;
        String d = deployment.toLowerCase(Locale.US);
        return d.startsWith("gpt-5") || d.startsWith("o1") || d.startsWith("o3") || d.startsWith("o4");
    }
}
