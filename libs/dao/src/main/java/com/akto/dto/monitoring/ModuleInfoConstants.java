package com.akto.dto.monitoring;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ModuleInfoConstants {

    public static final String REDACTED_PLACEHOLDER = "********";

    public static final Set<String> SECRET_ENV_KEYS = new HashSet<String>() {{
        add("AKTO_API_TOKEN");
    }};

    // Whitelist of environment variables that are safe to expose/collect
    // This is the single source of truth for allowed environment variables
    // Organized by module type
    public static final Map<ModuleInfo.ModuleType, Map<String, String>> ALLOWED_ENV_KEYS_BY_MODULE = new HashMap<ModuleInfo.ModuleType, Map<String, String>>() {{
        put(ModuleInfo.ModuleType.TRAFFIC_COLLECTOR, new HashMap<String, String>() {{
            put("AKTO_KAFKA_BROKER_MAL", "Kafka Broker MAL");
            put("AKTO_KAFKA_BROKER_URL", "Kafka Broker URL");
            put("AKTO_TRAFFIC_BATCH_SIZE", "Traffic Batch Size");
            put("AKTO_TRAFFIC_BATCH_TIME_SECS", "Traffic Batch Time (Seconds)");
            put("AKTO_LOG_LEVEL", "Log Level");
            put("DEBUG_URLS", "Debug URLs (url1,url2,url3)");
            put("AKTO_K8_METADATA_CAPTURE", "K8 Metadata Capture");
            put("AKTO_THREAT_ENABLED", "Threat Enabled");
            put("AKTO_IGNORE_ENVOY_PROXY_CALLS", "Ignore Envoy Proxy Calls");
            put("AKTO_IGNORE_IP_TRAFFIC", "Ignore IP Traffic");
        }});

        put(ModuleInfo.ModuleType.AKTO_AGENT_GATEWAY, new HashMap<String, String>() {{
            put("AKTO_API_BASE_URL", "URL for Akto data ingestion service");
            put("APP_SERVER_NAME", "Name to identify this agent server for policy filtering, If not set, will be automatically extracted from APP_URL hostname");
            put("SKIP_THREAT", "Set to true to skip sending threat reports to Akto");
            put("REQUEST_TIMEOUT", "Timeout for forwarding requests to AI agent");
            put("ALLOWED_HTTP_METHODS", "Comma-separated list of allowed HTTP methods");
            put("APPLY_GUARDRAILS_TO_SSE", "Apply guardrails to SSE (Server-Sent Events / text/event-stream) requests");
            put("GUARDRAIL_ENDPOINTS", "Specific endpoints to apply guardrails");
            put("GUARDRAIL_FIELD_MAPPING", "Per-endpoint JSON path for user prompt field, Format: METHOD:PATH:fieldPath");
        }});

        put(ModuleInfo.ModuleType.THREAT_DETECTION, new HashMap<String, String>() {{
            put("AKTO_TRAFFIC_KAFKA_BOOTSTRAP_SERVER", "Traffic Kafka Bootstrap Server");
            put("AKTO_INTERNAL_KAFKA_BOOTSTRAP_SERVER", "Internal Kafka Bootstrap Server");
            put("AKTO_THREAT_DETECTION_LOCAL_REDIS_URI", "Local Redis URI");
            put("AGGREGATION_RULES_ENABLED", "Aggregation Rules Enabled");
            put("AKTO_THREAT_PROTECTION_BACKEND_URL", "Threat Protection Backend URL");
            put("AKTO_MONGO_CONN", "MongoDB Connection String");
            put("RUNTIME_MODE", "Runtime Mode");
            put("AKTO_THREAT_PROTECTION_BACKEND_TOKEN", "Threat Protection Backend Token");
            put("DATABASE_ABSTRACTOR_SERVICE_TOKEN", "Database Abstractor Token");
            put("AKTO_LOG_LEVEL", "Log Level");
        }});

        put(ModuleInfo.ModuleType.MCP_ENDPOINT_SHIELD, new java.util.LinkedHashMap<String, String>() {{
            put("AKTO_API_TOKEN", "Akto API Token");
            put("DATABASE_ABSTRACTOR_SERVICE_URL", "Database Abstractor Service URL");
            put("AKTO_API_BASE_URL", "Akto API Base URL");
            put("ENABLE_MCP_WRAP_CLAUDE", "Enable MCP Wrap for Claude");
            put("ENABLE_MCP_WRAP_CURSOR", "Enable MCP Wrap for Cursor");
            put("ENABLE_MCP_WRAP_VSCODE", "Enable MCP Wrap for VS Code");
            put("ENABLE_MCP_WRAP_WINDSURF", "Enable MCP Wrap for Windsurf");
            put("ENABLE_MCP_WRAP_CODEX", "Enable MCP Wrap for Codex");
            put("ENABLE_MCP_WRAP_GITHUB_CLI", "Enable MCP Wrap for GitHub CLI");
            put("ENABLE_MCP_WRAP_ANTIGRAVITY", "Enable MCP Wrap for Antigravity");
            put("ENABLE_MCP_HOOKS_CLAUDE", "MCP Hooks path for Claude");
            put("ENABLE_MCP_HOOKS_CURSOR", "MCP Hooks path for Cursor");
            put("ENABLE_PROMPT_HOOKS_CLAUDE", "Prompt Hooks path for Claude");
            put("ENABLE_PROMPT_HOOKS_CURSOR", "Prompt Hooks path for Cursor");
        }});
    }};

    private ModuleInfoConstants() {
        // Private constructor to prevent instantiation
    }
}
