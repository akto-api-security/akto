package com.akto.gateway;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;

public class AdapterFactory {

    private static final Logger logger = LogManager.getLogger(AdapterFactory.class);

    private final StandardGuardrailsAdapter standardAdapter;
    private final LightLLMAdapter lightLLMAdapter;

    public AdapterFactory(GuardrailsClient guardrailsClient) {
        this.standardAdapter = new StandardGuardrailsAdapter(guardrailsClient);
        this.lightLLMAdapter = new LightLLMAdapter(guardrailsClient);
        logger.info("AdapterFactory initialized with standard and lightllm adapters");
    }

    public GuardrailsAdapter selectAdapter(Map<String, Object> queryParams) {
        if (queryParams == null || queryParams.isEmpty()) {
            logger.debug("No query params - using standard adapter");
            return standardAdapter;
        }

        // Check for akto_connector parameter
        Object connectorValue = queryParams.get("akto_connector");
        if (connectorValue != null) {
            String connector = connectorValue.toString();

            if ("lightllm".equalsIgnoreCase(connector)) {
                logger.info("Selecting LightLLM adapter based on akto_connector=lightllm");
                return lightLLMAdapter;
            }
        }

        logger.debug("Using standard adapter (default)");
        return standardAdapter;
    }

    public boolean shouldApplyGuardrails(Map<String, Object> queryParams) {
        if (queryParams == null || queryParams.isEmpty()) {
            return false;
        }

        // Check for guardrails parameter (from URL query string, always comes as String)
        String guardrailsValue = String.valueOf(queryParams.getOrDefault("guardrails", ""));
        boolean result = "true".equalsIgnoreCase(guardrailsValue);

        logger.debug("guardrails parameter value: {}, result: {}", guardrailsValue, result);
        return result;
    }

    public StandardGuardrailsAdapter getStandardAdapter() {
        return standardAdapter;
    }

    public LightLLMAdapter getLightLLMAdapter() {
        return lightLLMAdapter;
    }
}
