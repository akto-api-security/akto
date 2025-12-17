package com.akto.config;

public class GuardrailsConfig {

    // Environment variable keys
    private static final String ENABLE_GUARDRAILS_KEY = "ENABLE_GUARDRAILS";
    private static final String GUARDRAILS_TOPIC_KEY = "GUARDRAILS_TOPIC";

    // Default values
    private static final String DEFAULT_GUARDRAILS_TOPIC = "akto.guardrails";

    // Sinlgeton Instance
    private static volatile GuardrailsConfig instance;

    private final boolean enabled;
    private final String topicName;

    private GuardrailsConfig() {
        String enabledEnv = System.getenv(ENABLE_GUARDRAILS_KEY);
        this.enabled = "true".equals(enabledEnv);

        String topicEnv = System.getenv(GUARDRAILS_TOPIC_KEY);
        this.topicName = (topicEnv != null && !topicEnv.isEmpty())
            ? topicEnv
            : DEFAULT_GUARDRAILS_TOPIC;
    }

    public static GuardrailsConfig getInstance() {
        if (instance == null) {
            synchronized (GuardrailsConfig.class) {
                if (instance == null) {
                    instance = new GuardrailsConfig();
                }
            }
        }
        return instance;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getTopicName() {
        return topicName;
    }

    @Override
    public String toString() {
        return String.format("GuardrailsConfig{enabled=%s, topicName='%s'}",
            enabled, topicName);
    }
}
