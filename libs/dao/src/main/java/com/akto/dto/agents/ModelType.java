package com.akto.dto.agents;

public enum ModelType {

    OPENAI,
    ANTHROPIC,
    AZURE_OPENAI,
    MISTRAL_AI,
    GEMINI,
    GITHUB_COPILOT,
    OLLAMA,
    CLOUDFLARE,
    // Saved from the dashboard's Agents configuration page; must exist here too or
    // fetchAgentModels (which queries per known type) never returns those models.
    DATABRICKS,
    VERTEX_AI,
    BEDROCK

}
