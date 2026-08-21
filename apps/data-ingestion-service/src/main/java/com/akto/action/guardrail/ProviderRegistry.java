package com.akto.action.guardrail;

import java.util.HashMap;
import java.util.Map;

/**
 * Registry of {@link ProviderAdapter}s keyed by provider name (the {provider}
 * path segment). Adding a provider is one register() call here — no new action.
 */
public class ProviderRegistry {

    private static final Map<String, ProviderAdapter> ADAPTERS = new HashMap<>();

    static {
        register(new ClaudeAdapter());
        // register(new OpenAiAdapter()); // future: ChatGPT, etc.
    }

    private ProviderRegistry() {
    }

    public static void register(ProviderAdapter adapter) {
        ADAPTERS.put(adapter.name().toLowerCase(), adapter);
    }

    /** Returns the adapter for the provider name, or null if none is registered. */
    public static ProviderAdapter get(String name) {
        return name == null ? null : ADAPTERS.get(name.toLowerCase());
    }
}
