package com.akto.jobs.executors.strategy;

import java.util.HashMap;
import java.util.Map;

import static com.akto.jobs.executors.AIAgentConnectorConstants.*;

/**
 * Factory for creating AI Agent Connector Strategy instances.
 * Uses the Factory pattern to instantiate the appropriate strategy based on connector type.
 */
public class AIAgentConnectorStrategyFactory {

    private static final Map<String, AIAgentConnectorStrategy> strategies = new HashMap<>();

    static {
        // Register all available strategies
        registerStrategy(new N8NConnectorStrategy());
        registerStrategy(new LangchainConnectorStrategy());
        registerStrategy(new CopilotStudioConnectorStrategy());
    }

    /**
     * Register a strategy in the factory
     */
    private static void registerStrategy(AIAgentConnectorStrategy strategy) {
        strategies.put(strategy.getConnectorType(), strategy);
    }

    /**
     * Get the appropriate strategy for the given connector type
     * @param connectorType The connector type (N8N, LANGCHAIN, COPILOT_STUDIO)
     * @return The strategy instance for the connector type
     * @throws IllegalArgumentException if connector type is not supported
     */
    public static AIAgentConnectorStrategy getStrategy(String connectorType) {
        AIAgentConnectorStrategy strategy = strategies.get(connectorType);

        if (strategy == null) {
            throw new IllegalArgumentException("Unsupported connector type: " + connectorType);
        }

        return strategy;
    }

    /**
     * Check if a connector type is supported
     * @param connectorType The connector type to check
     * @return true if supported, false otherwise
     */
    public static boolean isSupported(String connectorType) {
        return strategies.containsKey(connectorType);
    }

    /**
     * Get all supported connector types
     * @return Array of supported connector type strings
     */
    public static String[] getSupportedConnectorTypes() {
        return strategies.keySet().toArray(new String[0]);
    }
}
