package com.akto.jsonrpc;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class McpToolDescriptionsRegistry {

    private static volatile Map<String, String> toolDescriptions = new HashMap<>();

    private McpToolDescriptionsRegistry() {}

    public static void set(Map<String, String> descriptions) {
        toolDescriptions = descriptions != null ? descriptions : new HashMap<>();
    }

    public static String get(String toolName) {
        if (toolName == null) return null;
        return toolDescriptions.get(toolName);
    }

    public static Map<String, String> getAll() {
        return Collections.unmodifiableMap(toolDescriptions);
    }
}
