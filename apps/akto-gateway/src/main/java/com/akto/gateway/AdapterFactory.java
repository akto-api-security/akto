package com.akto.gateway;

import com.akto.log.LoggerMaker;

import java.util.Map;

public class AdapterFactory {

    private static final LoggerMaker loggerMaker = new LoggerMaker(AdapterFactory.class, LoggerMaker.LogDb.DATA_INGESTION);

    private static final String CONNECTOR_LITELLM = "litellm";
    private static final String CONNECTOR_CLAUDE_CODE_CLI = "claude_code_cli";
    private static final String CONNECTOR_CURSOR = "cursor";

    private final StandardGuardrailsAdapter standardAdapter;
    private final LiteLLMAdapter liteLLMAdapter;

    public AdapterFactory(GuardrailsClient guardrailsClient) {
        this.standardAdapter = new StandardGuardrailsAdapter(guardrailsClient);
        this.liteLLMAdapter = new LiteLLMAdapter(guardrailsClient);
        loggerMaker.infoAndAddToDb("AdapterFactory initialized with standard and litellm adapters");
    }

    public GuardrailsAdapter selectAdapter(Map<String, Object> queryParams) {
        if (queryParams == null || queryParams.isEmpty()) {
            loggerMaker.debugAndAddToDb("No query params - using standard adapter");
            return standardAdapter;
        }

        // Check for akto_connector parameter
        Object connectorValue = queryParams.get("akto_connector");
        if (connectorValue != null) {
            String connector = connectorValue.toString();

            if (CONNECTOR_LITELLM.equalsIgnoreCase(connector) || CONNECTOR_CLAUDE_CODE_CLI.equalsIgnoreCase(connector) || CONNECTOR_CURSOR.equalsIgnoreCase(connector)) {
                loggerMaker.infoAndAddToDb("Selecting LiteLLM adapter based on akto_connector=litellm");
                return liteLLMAdapter;
            }
        }

        loggerMaker.debugAndAddToDb("Using standard adapter (default)");
        return standardAdapter;
    }

    public boolean shouldApplyGuardrails(Map<String, Object> queryParams) {
        if (queryParams == null || queryParams.isEmpty()) {
            return false;
        }

        // Check for guardrails parameter (from URL query string, always comes as String)
        String guardrailsValue = String.valueOf(queryParams.getOrDefault("guardrails", ""));
        boolean result = "true".equalsIgnoreCase(guardrailsValue);

        loggerMaker.debugAndAddToDb("guardrails parameter value: {}, result: {}", guardrailsValue, result);
        return result;
    }

    public StandardGuardrailsAdapter getStandardAdapter() {
        return standardAdapter;
    }

    public LiteLLMAdapter getLiteLLMAdapter() {
        return liteLLMAdapter;
    }
}
