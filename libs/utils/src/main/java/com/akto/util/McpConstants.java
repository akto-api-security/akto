package com.akto.util;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Constants for MCP (Model Context Protocol) reconnaissance and scanning
 */
public final class McpConstants {
    
    private McpConstants() {
        // Private constructor to prevent instantiation
    }
    
    /**
     * MCP Protocol version
     */
    public static final String MCP_PROTOCOL_VERSION = "1.0.0";
    
    /**
     * Common MCP ports to scan
     */
    public static final List<Integer> COMMON_MCP_PORTS = Collections.unmodifiableList(Arrays.asList(
        3000, 3001, 5000, 5001, 5173,
        8080, 8081, 8082, 8088, 8089, 8090,
        8765, 9000, 9090, 9091, 9092,
        4000, 4001, 4002,
        7777, 8000, 8888
    ));
    
    /**
     * MCP endpoints to check
     */
    public static final List<String> MCP_ENDPOINTS = Collections.unmodifiableList(Arrays.asList(
        "/mcp",
        "/mcp/sse",
        "/mcp/v1",
        "/mcp/stream",
        "/api/mcp",
        "/v1/mcp",
        "/messages",
        "/sse",
        "/.well-known/mcp.json",
        "/jsonrpc",
        "/rpc",
        "/"
    ));
    
    /**
     * MCP indicators for detection in response content
     */
    public static final List<String> MCP_INDICATORS = Collections.unmodifiableList(Arrays.asList(
        "model-context-protocol",
        "model context protocol",
        "mcp server",
        "mcp-server",
        "text/event-stream",
        "server-sent events",
        "application/x-ndjson",
        "\"jsonrpc\"",
        "\"jsonrpc\": \"2.0\"",
        "\"method\": \"initialize\"",
        "\"method\": \"tools/list\"",
        "\"method\": \"resources/list\"",
        "\"method\": \"prompts/list\"",
        "initialize",
        "capabilities",
        "clientInfo",
        "serverInfo",
        "protocolVersion",
        "fastapi mcp",
        "python-mcp",
        "mcp-typescript",
        "claude",
        "anthropic",
        "tools/list",
        "resources/list",
        "prompts/list",
        "mcp development",
        "mcp dev",
        "mcp-dev",
        "mcp test",
        "localhost mcp",
        "html:\"Model Context Protocol\"",
        "html:\"mcp server\"",
        "html:\"jsonrpc\"",
        "html:\"text/event-stream\"",
        "tools/call",
        "resources/read",
        "anthropic mcp",
        "claude mcp",
        "anthropic/mcp",
        "MCP error",
        "jsonrpc error",
        "invalid method",
        "method not found",
        "stream json",
        "streaming jsonrpc",
        "real-time json",
        "websocket mcp",
        "access-control-allow-methods",
        "access-control-allow-headers",
        "x-frame-options",
        "nodejs mcp",
        "node mcp",
        "express mcp",
        "fastify mcp",
        "python mcp",
        "flask mcp",
        "django mcp",
        "starlette mcp",
        "/health mcp",
        "/status mcp",
        "/debug mcp",
        "/info mcp",
        "mcp",
        "model context",
        "context protocol",
        "github mcp",
        "docs mcp",
        "documentation mcp",
        "readme mcp"
    ));
    
    /**
     * Pre-compiled regex patterns for MCP detection
     */
    public static final List<Pattern> MCP_PATTERNS = Collections.unmodifiableList(Arrays.asList(
        Pattern.compile("mcp[-_\\s]?server", Pattern.CASE_INSENSITIVE),
        Pattern.compile("model[-_\\s]?context[-_\\s]?protocol", Pattern.CASE_INSENSITIVE),
        Pattern.compile("json[-_\\s]?rpc.*2\\.0", Pattern.CASE_INSENSITIVE),
        Pattern.compile("text/event-stream", Pattern.CASE_INSENSITIVE)
    ));
    
    /**
     * JSON-RPC related constants
     */
    public static final String JSONRPC_VERSION = "2.0";
    public static final String JSONRPC_METHOD_INITIALIZE = "initialize";
    public static final String JSONRPC_METHOD_TOOLS_LIST = "tools/list";
    public static final String JSONRPC_METHOD_RESOURCES_LIST = "resources/list";
    public static final String JSONRPC_METHOD_PROMPTS_LIST = "prompts/list";
    
    /**
     * Content types
     */
    public static final String CONTENT_TYPE_EVENT_STREAM = "text/event-stream";
    public static final String CONTENT_TYPE_NDJSON = "application/x-ndjson";
    public static final String CONTENT_TYPE_JSON = "application/json";
    
    /**
     * Cache configuration
     */
    public static final long FAILED_IP_CACHE_TIME_MS = 3600000; // 1 hour
    public static final int MAX_CACHE_SIZE = 10000;
    
    /**
     * Scanning configuration defaults
     */
    public static final int DEFAULT_TIMEOUT_MS = 2000;
    public static final int DEFAULT_BATCH_SIZE = 500;
    public static final int DEFAULT_MAX_CONCURRENT = 100;
    public static final int MAX_CONTENT_CHECK_SIZE = 5000;
}