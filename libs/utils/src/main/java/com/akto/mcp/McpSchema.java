package com.akto.mcp;

import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Data;

public final class McpSchema {
    private static final LoggerMaker logger = new LoggerMaker(McpSchema.class, LogDb.RUNTIME);
    private McpSchema() {}
    public static final String LATEST_PROTOCOL_VERSION = "2024-11-05";
    public static final String JSONRPC_VERSION = "2.0";
    public static final String METHOD_INITIALIZE = "initialize";
    public static final String METHOD_NOTIFICATION_INITIALIZED = "notifications/initialized";
    public static final String METHOD_PING = "ping";
    public static final String METHOD_TOOLS_LIST = "tools/list";
    public static final String METHOD_TOOLS_CALL = "tools/call";
    public static final String METHOD_NOTIFICATION_TOOLS_LIST_CHANGED = "notifications/tools/list_changed";
    public static final String METHOD_RESOURCES_LIST = "resources/list";
    public static final String METHOD_RESOURCES_READ = "resources/read";
    public static final String METHOD_NOTIFICATION_RESOURCES_LIST_CHANGED = "notifications/resources/list_changed";
    public static final String METHOD_RESOURCES_TEMPLATES_LIST = "resources/templates/list";
    public static final String METHOD_RESOURCES_SUBSCRIBE = "resources/subscribe";
    public static final String METHOD_RESOURCES_UNSUBSCRIBE = "resources/unsubscribe";
    public static final String METHOD_PROMPT_LIST = "prompts/list";
    public static final String METHOD_PROMPT_GET = "prompts/get";
    public static final String METHOD_NOTIFICATION_PROMPTS_LIST_CHANGED = "notifications/prompts/list_changed";
    public static final String METHOD_COMPLETION_COMPLETE = "completion/complete";
    public static final String METHOD_LOGGING_SET_LEVEL = "logging/setLevel";
    public static final String METHOD_NOTIFICATION_MESSAGE = "notifications/message";
    public static final String METHOD_ROOTS_LIST = "roots/list";
    public static final String METHOD_NOTIFICATION_ROOTS_LIST_CHANGED = "notifications/roots/list_changed";
    public static final String METHOD_SAMPLING_CREATE_MESSAGE = "sampling/createMessage";
    public static final String MCP_NOTIFICATIONS_CANCELLED_METHOD = "notifications/cancelled";
    public static final String MCP_NOTIFICATIONS_PROGRESS_METHOD = "notifications/progress";
    public static final String MCP_NOTIFICATIONS_RESOURCES_UPDATED_METHOD = "notifications/resources/updated";
    public static final String MCP_JSONRPC_KEY = "jsonrpc";
    public static final String MCP_ERROR_KEY = "error";
    public static final String MCP_RESULT_KEY = "result";


    public static final Set<String> MCP_METHOD_SET = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
        McpSchema.METHOD_TOOLS_LIST,
        McpSchema.METHOD_TOOLS_CALL,
        McpSchema.METHOD_PROMPT_LIST,
        McpSchema.METHOD_PROMPT_GET,
        McpSchema.METHOD_RESOURCES_LIST,
        McpSchema.METHOD_RESOURCES_READ,
        McpSchema.METHOD_RESOURCES_TEMPLATES_LIST,
        McpSchema.METHOD_PING,
        McpSchema.METHOD_INITIALIZE,
        McpSchema.MCP_NOTIFICATIONS_CANCELLED_METHOD,
        McpSchema.METHOD_COMPLETION_COMPLETE,
        McpSchema.METHOD_NOTIFICATION_INITIALIZED,
        McpSchema.METHOD_LOGGING_SET_LEVEL,
        McpSchema.METHOD_RESOURCES_SUBSCRIBE,
        McpSchema.METHOD_RESOURCES_UNSUBSCRIBE,
        McpSchema.METHOD_SAMPLING_CREATE_MESSAGE,
        McpSchema.METHOD_ROOTS_LIST,
        McpSchema.METHOD_NOTIFICATION_MESSAGE,
        McpSchema.MCP_NOTIFICATIONS_PROGRESS_METHOD,
        McpSchema.METHOD_NOTIFICATION_PROMPTS_LIST_CHANGED,
        McpSchema.METHOD_NOTIFICATION_RESOURCES_LIST_CHANGED,
        McpSchema.MCP_NOTIFICATIONS_RESOURCES_UPDATED_METHOD,
        McpSchema.METHOD_NOTIFICATION_ROOTS_LIST_CHANGED,
        McpSchema.METHOD_NOTIFICATION_TOOLS_LIST_CHANGED
    )));


    public static final class ErrorCodes {
        public static final int PARSE_ERROR = -32700;
        public static final int INVALID_REQUEST = -32600;
        public static final int METHOD_NOT_FOUND = -32601;
        public static final int INVALID_PARAMS = -32602;
        public static final int INTERNAL_ERROR = -32603;
    }
    public interface Request {}
    private static final TypeReference<HashMap<String, Object>> MAP_TYPE_REF = new TypeReference<HashMap<String, Object>>() {};
    public static Object deserializeJsonRpcMessage(ObjectMapper objectMapper, String jsonText) throws IOException {
        logger.debug("Received JSON message: {}", jsonText);
        Map<String, Object> map = objectMapper.readValue(jsonText, MAP_TYPE_REF);
        if (map.containsKey("method") && map.containsKey("id")) {
            return objectMapper.convertValue(map, JSONRPCRequest.class);
        } else if (map.containsKey("method") && !map.containsKey("id")) {
            return objectMapper.convertValue(map, JSONRPCNotification.class);
        } else if (map.containsKey("result") || map.containsKey("error")) {
            return objectMapper.convertValue(map, JSONRPCResponse.class);
        }
        throw new IllegalArgumentException("Cannot deserialize JSONRPCMessage: " + jsonText);
    }
    public interface JSONRPCMessage {
        String getJsonrpc();
    }
    @Data
    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class JSONRPCRequest implements JSONRPCMessage {
        @JsonProperty("jsonrpc")
        private final String jsonrpc;
        @JsonProperty("method")
        private final String method;
        @JsonProperty("id")
        private final Object id;
        @JsonProperty("params")
        private final Object params;
        public JSONRPCRequest(@JsonProperty("jsonrpc") String jsonrpc, @JsonProperty("method") String method, @JsonProperty("id") Object id, @JsonProperty("params") Object params) {
            this.jsonrpc = jsonrpc;
            this.method = method;
            this.id = id;
            this.params = params;
        }
        public String getJsonrpc() { return jsonrpc; }
        public String getMethod() { return method; }
        public Object getId() { return id; }
        public Object getParams() { return params; }
    }
    @Data
    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class JSONRPCNotification implements JSONRPCMessage {
        @JsonProperty("jsonrpc")
        private final String jsonrpc;
        @JsonProperty("method")
        private final String method;
        @JsonProperty("params")
        private final Object params;
        public JSONRPCNotification(@JsonProperty("jsonrpc") String jsonrpc, @JsonProperty("method") String method, @JsonProperty("params") Object params) {
            this.jsonrpc = jsonrpc;
            this.method = method;
            this.params = params;
        }
        public String getJsonrpc() { return jsonrpc; }
        public String getMethod() { return method; }
        public Object getParams() { return params; }
    }
    @Data
    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class JSONRPCResponse implements JSONRPCMessage {
        @JsonProperty("jsonrpc")
        private final String jsonrpc;
        @JsonProperty("id")
        private final Object id;
        @JsonProperty("result")
        private final Object result;
        @JsonProperty("error")
        private final JSONRPCError error;
        public JSONRPCResponse(@JsonProperty("jsonrpc") String jsonrpc, @JsonProperty("id") Object id, @JsonProperty("result") Object result, @JsonProperty("error") JSONRPCError error) {
            this.jsonrpc = jsonrpc;
            this.id = id;
            this.result = result;
            this.error = error;
        }
        public String getJsonrpc() { return jsonrpc; }
        public Object getId() { return id; }
        public Object getResult() { return result; }
        public JSONRPCError getError() { return error; }
        @Data
        @JsonInclude(JsonInclude.Include.NON_ABSENT)
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class JSONRPCError {
            @JsonProperty("code")
            private final int code;
            @JsonProperty("message")
            private final String message;
            @JsonProperty("data")
            private final Object data;
            public JSONRPCError(@JsonProperty("code") int code, @JsonProperty("message") String message, @JsonProperty("data") Object data) {
                this.code = code;
                this.message = message;
                this.data = data;
            }
            public int getCode() { return code; }
            public String getMessage() { return message; }
            public Object getData() { return data; }
        }
    }
    @Data
    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class InitializeRequest implements Request {
        @JsonProperty("protocolVersion")
        private final String protocolVersion;
        @JsonProperty("capabilities")
        private final ClientCapabilities capabilities;
        @JsonProperty("clientInfo")
        private final Implementation clientInfo;
        public InitializeRequest(@JsonProperty("protocolVersion") String protocolVersion,
            @JsonProperty("capabilities") ClientCapabilities capabilities,
            @JsonProperty("clientInfo") Implementation clientInfo) {
            this.protocolVersion = protocolVersion;
            this.capabilities = capabilities;
            this.clientInfo = clientInfo;
        }
        public String getProtocolVersion() { return protocolVersion; }
        public ClientCapabilities getCapabilities() { return capabilities; }
        public Implementation getClientInfo() { return clientInfo; }
    }
    @Data
    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class InitializeResult {
        @JsonProperty("protocolVersion")
        private final String protocolVersion;
        @JsonProperty("capabilities")
        private final ServerCapabilities capabilities;
        @JsonProperty("serverInfo")
        private final Implementation serverInfo;
        @JsonProperty("instructions")
        private final String instructions;
        public InitializeResult(@JsonProperty("protocolVersion") String protocolVersion,
            @JsonProperty("capabilities") ServerCapabilities capabilities,
            @JsonProperty("serverInfo") Implementation serverInfo,
            @JsonProperty("instructions") String instructions) {
            this.protocolVersion = protocolVersion;
            this.capabilities = capabilities;
            this.serverInfo = serverInfo;
            this.instructions = instructions;
        }
        public String getProtocolVersion() { return protocolVersion; }
        public ServerCapabilities getCapabilities() { return capabilities; }
        public Implementation getServerInfo() { return serverInfo; }
        public String getInstructions() { return instructions; }
    }
    @Data
    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ClientCapabilities {
        @JsonProperty("experimental")
        private final Map<String, Object> experimental;
        @JsonProperty("roots")
        private final RootCapabilities roots;
        @JsonProperty("sampling")
        private final Sampling sampling;
        public ClientCapabilities(@JsonProperty("experimental") Map<String, Object> experimental,
            @JsonProperty("roots") RootCapabilities roots,
            @JsonProperty("sampling") Sampling sampling) {
            this.experimental = experimental;
            this.roots = roots;
            this.sampling = sampling;
        }
        public Map<String, Object> getExperimental() { return experimental; }
        public RootCapabilities getRoots() { return roots; }
        public Sampling getSampling() { return sampling; }
        @Data
        @JsonInclude(JsonInclude.Include.NON_ABSENT)
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class RootCapabilities {
            @JsonProperty("listChanged")
            private final Boolean listChanged;
            public RootCapabilities(@JsonProperty("listChanged") Boolean listChanged) {
                this.listChanged = listChanged;
            }
            public Boolean getListChanged() { return listChanged; }
        }
        @Data
        public static class Sampling {
            public Sampling() {}
        }
        @Data
        public static class Builder {
            private Map<String, Object> experimental;
            private RootCapabilities roots;
            private Sampling sampling;
            public Builder experimental(Map<String, Object> experimental) {
                this.experimental = experimental;
                return this;
            }
            public Builder roots(Boolean listChanged) {
                this.roots = new RootCapabilities(listChanged);
                return this;
            }
            public Builder sampling() {
                this.sampling = new Sampling();
                return this;
            }
            public ClientCapabilities build() {
                return new ClientCapabilities(experimental, roots, sampling);
            }
        }
    }
    @Data
    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ServerCapabilities {
        @JsonProperty("completions")
        private final CompletionCapabilities completions;
        @JsonProperty("experimental")
        private final Map<String, Object> experimental;
        @JsonProperty("logging")
        private final LoggingCapabilities logging;
        @JsonProperty("prompts")
        private final PromptCapabilities prompts;
        @JsonProperty("resources")
        private final ResourceCapabilities resources;
        @JsonProperty("tools")
        private final ToolCapabilities tools;
        public ServerCapabilities(@JsonProperty("completions") CompletionCapabilities completions,
            @JsonProperty("experimental") Map<String, Object> experimental,
            @JsonProperty("logging") LoggingCapabilities logging,
            @JsonProperty("prompts") PromptCapabilities prompts,
            @JsonProperty("resources") ResourceCapabilities resources,
            @JsonProperty("tools") ToolCapabilities tools) {
            this.completions = completions;
            this.experimental = experimental;
            this.logging = logging;
            this.prompts = prompts;
            this.resources = resources;
            this.tools = tools;
        }
        public CompletionCapabilities getCompletions() { return completions; }
        public Map<String, Object> getExperimental() { return experimental; }
        public LoggingCapabilities getLogging() { return logging; }
        public PromptCapabilities getPrompts() { return prompts; }
        public ResourceCapabilities getResources() { return resources; }
        public ToolCapabilities getTools() { return tools; }
        @Data
        @JsonInclude(JsonInclude.Include.NON_ABSENT)
        public static class CompletionCapabilities {
            public CompletionCapabilities() {}
        }
        @Data
        @JsonInclude(JsonInclude.Include.NON_ABSENT)
        public static class LoggingCapabilities {
            public LoggingCapabilities() {}
        }
        @Data
        @JsonInclude(JsonInclude.Include.NON_ABSENT)
        public static class PromptCapabilities {
            @JsonProperty("listChanged")
            private final Boolean listChanged;
            public PromptCapabilities(@JsonProperty("listChanged") Boolean listChanged) {
                this.listChanged = listChanged;
            }
            public Boolean getListChanged() { return listChanged; }
        }
        @Data
        @JsonInclude(JsonInclude.Include.NON_ABSENT)
        public static class ResourceCapabilities {
            @JsonProperty("subscribe")
            private final Boolean subscribe;
            @JsonProperty("listChanged")
            private final Boolean listChanged;
            public ResourceCapabilities(@JsonProperty("subscribe") Boolean subscribe,
                @JsonProperty("listChanged") Boolean listChanged) {
                this.subscribe = subscribe;
                this.listChanged = listChanged;
            }
            public Boolean getSubscribe() { return subscribe; }
            public Boolean getListChanged() { return listChanged; }
        }
        @Data
        @JsonInclude(JsonInclude.Include.NON_ABSENT)
        public static class ToolCapabilities {
            @JsonProperty("listChanged")
            private final Boolean listChanged;
            public ToolCapabilities(@JsonProperty("listChanged") Boolean listChanged) {
                this.listChanged = listChanged;
            }
            public Boolean getListChanged() { return listChanged; }
        }
        @Data
        public static class Builder {
            private CompletionCapabilities completions;
            private Map<String, Object> experimental;
            private LoggingCapabilities logging = new LoggingCapabilities();
            private PromptCapabilities prompts;
            private ResourceCapabilities resources;
            private ToolCapabilities tools;
            public Builder completions() {
                this.completions = new CompletionCapabilities();
                return this;
            }
            public Builder experimental(Map<String, Object> experimental) {
                this.experimental = experimental;
                return this;
            }
            public Builder logging() {
                this.logging = new LoggingCapabilities();
                return this;
            }
            public Builder prompts(Boolean listChanged) {
                this.prompts = new PromptCapabilities(listChanged);
                return this;
            }
            public Builder resources(Boolean subscribe, Boolean listChanged) {
                this.resources = new ResourceCapabilities(subscribe, listChanged);
                return this;
            }
            public Builder tools(Boolean listChanged) {
                this.tools = new ToolCapabilities(listChanged);
                return this;
            }
            public ServerCapabilities build() {
                return new ServerCapabilities(completions, experimental, logging, prompts, resources, tools);
            }
        }
    }
    @Data
    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Implementation {
        @JsonProperty("name")
        private final String name;
        @JsonProperty("version")
        private final String version;
        public Implementation(@JsonProperty("name") String name, @JsonProperty("version") String version) {
            this.name = name;
            this.version = version;
        }
        public String getName() { return name; }
        public String getVersion() { return version; }
    }
    public enum Role {
        @JsonProperty("user") USER,
        @JsonProperty("assistant") ASSISTANT
    }
    public interface Annotated {
        Annotations getAnnotations();
    }
    @Data
    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Annotations {
        @JsonProperty("audience")
        private final List<Role> audience;
        @JsonProperty("priority")
        private final Double priority;
        public Annotations(@JsonProperty("audience") List<Role> audience, @JsonProperty("priority") Double priority) {
            this.audience = audience;
            this.priority = priority;
        }
        public List<Role> getAudience() { return audience; }
        public Double getPriority() { return priority; }
    }
    @Data
    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Resource implements Annotated {
        @JsonProperty("uri")
        private final String uri;
        @JsonProperty("name")
        private final String name;
        @JsonProperty("description")
        private final String description;
        @JsonProperty("mimeType")
        private final String mimeType;
        @JsonProperty("annotations")
        private final Annotations annotations;
        public Resource(@JsonProperty("uri") String uri,
            @JsonProperty("name") String name,
            @JsonProperty("description") String description,
            @JsonProperty("mimeType") String mimeType,
            @JsonProperty("annotations") Annotations annotations) {
            this.uri = uri;
            this.name = name;
            this.description = description;
            this.mimeType = mimeType;
            this.annotations = annotations;
        }
        public String getUri() { return uri; }
        public String getName() { return name; }
        public String getDescription() { return description; }
        public String getMimeType() { return mimeType; }
        public Annotations getAnnotations() { return annotations; }
    }
    @Data
    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ResourceTemplate implements Annotated {
        @JsonProperty("uriTemplate")
        private final String uriTemplate;
        @JsonProperty("name")
        private final String name;
        @JsonProperty("description")
        private final String description;
        @JsonProperty("mimeType")
        private final String mimeType;
        @JsonProperty("annotations")
        private final Annotations annotations;
        public ResourceTemplate(@JsonProperty("uriTemplate") String uriTemplate,
            @JsonProperty("name") String name,
            @JsonProperty("description") String description,
            @JsonProperty("mimeType") String mimeType,
            @JsonProperty("annotations") Annotations annotations) {
            this.uriTemplate = uriTemplate;
            this.name = name;
            this.description = description;
            this.mimeType = mimeType;
            this.annotations = annotations;
        }
        public String getUriTemplate() { return uriTemplate; }
        public String getName() { return name; }
        public String getDescription() { return description; }
        public String getMimeType() { return mimeType; }
        public Annotations getAnnotations() { return annotations; }
    }
    @Data
    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ListResourcesResult {
        @JsonProperty("resources")
        private final List<Resource> resources;
        @JsonProperty("nextCursor")
        private final String nextCursor;
        public ListResourcesResult(@JsonProperty("resources") List<Resource> resources,
            @JsonProperty("nextCursor") String nextCursor) {
            this.resources = resources;
            this.nextCursor = nextCursor;
        }
        public List<Resource> getResources() { return resources; }
        public String getNextCursor() { return nextCursor; }
    }
    @Data
    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ListResourceTemplatesResult {
        @JsonProperty("resourceTemplates")
        private final List<ResourceTemplate> resourceTemplates;
        @JsonProperty("nextCursor")
        private final String nextCursor;
        public ListResourceTemplatesResult(@JsonProperty("resourceTemplates") List<ResourceTemplate> resourceTemplates,
            @JsonProperty("nextCursor") String nextCursor) {
            this.resourceTemplates = resourceTemplates;
            this.nextCursor = nextCursor;
        }
        public List<ResourceTemplate> getResourceTemplates() { return resourceTemplates; }
        public String getNextCursor() { return nextCursor; }
    }
    @Data
    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ReadResourceRequest {
        @JsonProperty("uri")
        private final String uri;
        public ReadResourceRequest(@JsonProperty("uri") String uri) {
            this.uri = uri;
        }
        public String getUri() { return uri; }
    }
    @Data
    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ReadResourceResult {
        @JsonProperty("contents")
        private final List<ResourceContents> contents;
        public ReadResourceResult(@JsonProperty("contents") List<ResourceContents> contents) {
            this.contents = contents;
        }
        public List<ResourceContents> getContents() { return contents; }
    }
    @Data
    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SubscribeRequest {
        @JsonProperty("uri")
        private final String uri;
        public SubscribeRequest(@JsonProperty("uri") String uri) {
            this.uri = uri;
        }
        public String getUri() { return uri; }
    }
    @Data
    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class UnsubscribeRequest {
        @JsonProperty("uri")
        private final String uri;
        public UnsubscribeRequest(@JsonProperty("uri") String uri) {
            this.uri = uri;
        }
        public String getUri() { return uri; }
    }
    public interface ResourceContents {
        String getUri();
        String getMimeType();
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TextResourceContents implements ResourceContents {
        @JsonProperty("uri")
        private final String uri;
        @JsonProperty("mimeType")
        private final String mimeType;
        @JsonProperty("text")
        private final String text;
        public TextResourceContents(@JsonProperty("uri") String uri,
            @JsonProperty("mimeType") String mimeType,
            @JsonProperty("text") String text) {
            this.uri = uri;
            this.mimeType = mimeType;
            this.text = text;
        }
        public String getUri() { return uri; }
        public String getMimeType() { return mimeType; }
        public String getText() { return text; }
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BlobResourceContents implements ResourceContents {
        @JsonProperty("uri")
        private final String uri;
        @JsonProperty("mimeType")
        private final String mimeType;
        @JsonProperty("blob")
        private final String blob;
        public BlobResourceContents(@JsonProperty("uri") String uri,
            @JsonProperty("mimeType") String mimeType,
            @JsonProperty("blob") String blob) {
            this.uri = uri;
            this.mimeType = mimeType;
            this.blob = blob;
        }
        public String getUri() { return uri; }
        public String getMimeType() { return mimeType; }
        public String getBlob() { return blob; }
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Prompt {
        @JsonProperty("name")
        private final String name;
        @JsonProperty("description")
        private final String description;
        @JsonProperty("arguments")
        private final List<PromptArgument> arguments;
        public Prompt(@JsonProperty("name") String name,
            @JsonProperty("description") String description,
            @JsonProperty("arguments") List<PromptArgument> arguments) {
            this.name = name;
            this.description = description;
            this.arguments = arguments;
        }
        public String getName() { return name; }
        public String getDescription() { return description; }
        public List<PromptArgument> getArguments() { return arguments; }
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PromptArgument {
        @JsonProperty("name")
        private final String name;
        @JsonProperty("description")
        private final String description;
        @JsonProperty("required")
        private final Boolean required;
        public PromptArgument(@JsonProperty("name") String name,
            @JsonProperty("description") String description,
            @JsonProperty("required") Boolean required) {
            this.name = name;
            this.description = description;
            this.required = required;
        }
        public String getName() { return name; }
        public String getDescription() { return description; }
        public Boolean getRequired() { return required; }
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PromptMessage {
        @JsonProperty("role")
        private final Role role;
        @JsonProperty("content")
        private final Content content;
        public PromptMessage(@JsonProperty("role") Role role,
            @JsonProperty("content") Content content) {
            this.role = role;
            this.content = content;
        }
        public Role getRole() { return role; }
        public Content getContent() { return content; }
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ListPromptsResult {
        @JsonProperty("prompts")
        private final List<Prompt> prompts;
        @JsonProperty("nextCursor")
        private final String nextCursor;
        public ListPromptsResult(@JsonProperty("prompts") List<Prompt> prompts,
            @JsonProperty("nextCursor") String nextCursor) {
            this.prompts = prompts;
            this.nextCursor = nextCursor;
        }
        public List<Prompt> getPrompts() { return prompts; }
        public String getNextCursor() { return nextCursor; }
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GetPromptRequest {
        @JsonProperty("name")
        private final String name;
        @JsonProperty("arguments")
        private final Map<String, Object> arguments;
        public GetPromptRequest(@JsonProperty("name") String name,
            @JsonProperty("arguments") Map<String, Object> arguments) {
            this.name = name;
            this.arguments = arguments;
        }
        public String getName() { return name; }
        public Map<String, Object> getArguments() { return arguments; }
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GetPromptResult {
        @JsonProperty("description")
        private final String description;
        @JsonProperty("messages")
        private final List<PromptMessage> messages;
        public GetPromptResult(@JsonProperty("description") String description,
            @JsonProperty("messages") List<PromptMessage> messages) {
            this.description = description;
            this.messages = messages;
        }
        public String getDescription() { return description; }
        public List<PromptMessage> getMessages() { return messages; }
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ListToolsResult {
        @JsonProperty("tools")
        private final List<Tool> tools;
        @JsonProperty("nextCursor")
        private final String nextCursor;
        public ListToolsResult(@JsonProperty("tools") List<Tool> tools,
            @JsonProperty("nextCursor") String nextCursor) {
            this.tools = tools;
            this.nextCursor = nextCursor;
        }
        public List<Tool> getTools() { return tools; }
        public String getNextCursor() { return nextCursor; }
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class JsonSchema {
        @JsonProperty("type")
        private final String type;
        @JsonProperty("properties")
        private final Map<String, Object> properties;
        @JsonProperty("required")
        private final List<String> required;
        @JsonProperty("additionalProperties")
        private final Boolean additionalProperties;
        @JsonProperty("$defs")
        private final Map<String, Object> defs;
        @JsonProperty("definitions")
        private final Map<String, Object> definitions;
        public JsonSchema(@JsonProperty("type") String type,
            @JsonProperty("properties") Map<String, Object> properties,
            @JsonProperty("required") List<String> required,
            @JsonProperty("additionalProperties") Boolean additionalProperties,
            @JsonProperty("$defs") Map<String, Object> defs,
            @JsonProperty("definitions") Map<String, Object> definitions) {
            this.type = type;
            this.properties = properties;
            this.required = required;
            this.additionalProperties = additionalProperties;
            this.defs = defs;
            this.definitions = definitions;
        }
        public String getType() { return type; }
        public Map<String, Object> getProperties() { return properties; }
        public List<String> getRequired() { return required; }
        public Boolean getAdditionalProperties() { return additionalProperties; }
        public Map<String, Object> getDefs() { return defs; }
        public Map<String, Object> getDefinitions() { return definitions; }
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Tool {
        @JsonProperty("name")
        private final String name;
        @JsonProperty("description")
        private final String description;
        @JsonProperty("inputSchema")
        private final JsonSchema inputSchema;
        public Tool(@JsonProperty("name") String name,
            @JsonProperty("description") String description,
            @JsonProperty("inputSchema") JsonSchema inputSchema) {
            this.name = name;
            this.description = description;
            this.inputSchema = inputSchema;
        }
        public String getName() { return name; }
        public String getDescription() { return description; }
        public JsonSchema getInputSchema() { return inputSchema; }
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CallToolRequest {
        @JsonProperty("name")
        private final String name;
        @JsonProperty("arguments")
        private final Map<String, Object> arguments;
        public CallToolRequest(@JsonProperty("name") String name,
            @JsonProperty("arguments") Map<String, Object> arguments) {
            this.name = name;
            this.arguments = arguments;
        }
        public String getName() { return name; }
        public Map<String, Object> getArguments() { return arguments; }
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CallToolResult {
        @JsonProperty("content")
        private final List<Content> content;
        @JsonProperty("isError")
        private final Boolean isError;
        public CallToolResult(@JsonProperty("content") List<Content> content,
            @JsonProperty("isError") Boolean isError) {
            this.content = content;
            this.isError = isError;
        }
        public List<Content> getContent() { return content; }
        public Boolean getIsError() { return isError; }
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ModelPreferences {
        @JsonProperty("hints")
        private final List<ModelHint> hints;
        @JsonProperty("costPriority")
        private final Double costPriority;
        @JsonProperty("speedPriority")
        private final Double speedPriority;
        @JsonProperty("intelligencePriority")
        private final Double intelligencePriority;
        public ModelPreferences(@JsonProperty("hints") List<ModelHint> hints,
            @JsonProperty("costPriority") Double costPriority,
            @JsonProperty("speedPriority") Double speedPriority,
            @JsonProperty("intelligencePriority") Double intelligencePriority) {
            this.hints = hints;
            this.costPriority = costPriority;
            this.speedPriority = speedPriority;
            this.intelligencePriority = intelligencePriority;
        }
        public List<ModelHint> getHints() { return hints; }
        public Double getCostPriority() { return costPriority; }
        public Double getSpeedPriority() { return speedPriority; }
        public Double getIntelligencePriority() { return intelligencePriority; }
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ModelHint {
        @JsonProperty("name")
        private final String name;
        public ModelHint(@JsonProperty("name") String name) {
            this.name = name;
        }
        public String getName() { return name; }
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SamplingMessage {
        @JsonProperty("role")
        private final Role role;
        @JsonProperty("content")
        private final Content content;
        public SamplingMessage(@JsonProperty("role") Role role,
            @JsonProperty("content") Content content) {
            this.role = role;
            this.content = content;
        }
        public Role getRole() { return role; }
        public Content getContent() { return content; }
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CreateMessageRequest {
        @JsonProperty("messages")
        private final List<SamplingMessage> messages;
        @JsonProperty("modelPreferences")
        private final ModelPreferences modelPreferences;
        @JsonProperty("systemPrompt")
        private final String systemPrompt;
        @JsonProperty("includeContext")
        private final String includeContext;
        @JsonProperty("temperature")
        private final Double temperature;
        @JsonProperty("maxTokens")
        private final int maxTokens;
        @JsonProperty("stopSequences")
        private final List<String> stopSequences;
        @JsonProperty("metadata")
        private final Map<String, Object> metadata;
        public CreateMessageRequest(@JsonProperty("messages") List<SamplingMessage> messages,
            @JsonProperty("modelPreferences") ModelPreferences modelPreferences,
            @JsonProperty("systemPrompt") String systemPrompt,
            @JsonProperty("includeContext") String includeContext,
            @JsonProperty("temperature") Double temperature,
            @JsonProperty("maxTokens") int maxTokens,
            @JsonProperty("stopSequences") List<String> stopSequences,
            @JsonProperty("metadata") Map<String, Object> metadata) {
            this.messages = messages;
            this.modelPreferences = modelPreferences;
            this.systemPrompt = systemPrompt;
            this.includeContext = includeContext;
            this.temperature = temperature;
            this.maxTokens = maxTokens;
            this.stopSequences = stopSequences;
            this.metadata = metadata;
        }
        public List<SamplingMessage> getMessages() { return messages; }
        public ModelPreferences getModelPreferences() { return modelPreferences; }
        public String getSystemPrompt() { return systemPrompt; }
        public String getIncludeContext() { return includeContext; }
        public Double getTemperature() { return temperature; }
        public int getMaxTokens() { return maxTokens; }
        public List<String> getStopSequences() { return stopSequences; }
        public Map<String, Object> getMetadata() { return metadata; }
    }

    @Data
    public static class CreateMessageResult {
        @JsonProperty("role")
        private final Role role;
        @JsonProperty("content")
        private final Content content;
        @JsonProperty("model")
        private final String model;
        @JsonProperty("stopReason")
        private final String stopReason;
        public CreateMessageResult(@JsonProperty("role") Role role,
            @JsonProperty("content") Content content,
            @JsonProperty("model") String model,
            @JsonProperty("stopReason") String stopReason) {
            this.role = role;
            this.content = content;
            this.model = model;
            this.stopReason = stopReason;
        }
        public Role getRole() { return role; }
        public Content getContent() { return content; }
        public String getModel() { return model; }
        public String getStopReason() { return stopReason; }
    }

    @Data
    public static class ProgressNotification {
        @JsonProperty("progressToken")
        private final String progressToken;
        @JsonProperty("progress")
        private final double progress;
        @JsonProperty("total")
        private final Double total;
        public ProgressNotification(@JsonProperty("progressToken") String progressToken,
            @JsonProperty("progress") double progress,
            @JsonProperty("total") Double total) {
            this.progressToken = progressToken;
            this.progress = progress;
            this.total = total;
        }
        public String getProgressToken() { return progressToken; }
        public double getProgress() { return progress; }
        public Double getTotal() { return total; }
    }

    @Data
    public static class LoggingMessageNotification {
        @JsonProperty("level")
        private final LoggingLevel level;
        @JsonProperty("logger")
        private final String logger;
        @JsonProperty("data")
        private final String data;
        public LoggingMessageNotification(@JsonProperty("level") LoggingLevel level,
            @JsonProperty("logger") String logger,
            @JsonProperty("data") String data) {
            this.level = level;
            this.logger = logger;
            this.data = data;
        }
        public LoggingLevel getLevel() { return level; }
        public String getLogger() { return logger; }
        public String getData() { return data; }
    }

    public enum LoggingLevel {
        @JsonProperty("debug") DEBUG(0),
        @JsonProperty("info") INFO(1),
        @JsonProperty("notice") NOTICE(2),
        @JsonProperty("warning") WARNING(3),
        @JsonProperty("error") ERROR(4),
        @JsonProperty("critical") CRITICAL(5),
        @JsonProperty("alert") ALERT(6),
        @JsonProperty("emergency") EMERGENCY(7);
        private final int level;
        LoggingLevel(int level) { this.level = level; }
        public int getLevel() { return level; }
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SetLevelRequest {
        @JsonProperty("level")
        private final LoggingLevel level;
        public SetLevelRequest(@JsonProperty("level") LoggingLevel level) {
            this.level = level;
        }
        public LoggingLevel getLevel() { return level; }
    }

    public interface Content {}

    @Data
    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TextContent implements Content {
        @JsonProperty("audience")
        private final List<Role> audience;
        @JsonProperty("priority")
        private final Double priority;
        @JsonProperty("text")
        private final String text;
        public TextContent(@JsonProperty("audience") List<Role> audience,
            @JsonProperty("priority") Double priority,
            @JsonProperty("text") String text) {
            this.audience = audience;
            this.priority = priority;
            this.text = text;
        }
        public TextContent(String content) {
            this(null, null, content);
        }
        public List<Role> getAudience() { return audience; }
        public Double getPriority() { return priority; }
        public String getText() { return text; }
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ImageContent implements Content {
        @JsonProperty("audience")
        private final List<Role> audience;
        @JsonProperty("priority")
        private final Double priority;
        @JsonProperty("data")
        private final String data;
        @JsonProperty("mimeType")
        private final String mimeType;
        public ImageContent(@JsonProperty("audience") List<Role> audience,
            @JsonProperty("priority") Double priority,
            @JsonProperty("data") String data,
            @JsonProperty("mimeType") String mimeType) {
            this.audience = audience;
            this.priority = priority;
            this.data = data;
            this.mimeType = mimeType;
        }
        public List<Role> getAudience() { return audience; }
        public Double getPriority() { return priority; }
        public String getData() { return data; }
        public String getMimeType() { return mimeType; }
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class EmbeddedResource implements Content {
        @JsonProperty("audience")
        private final List<Role> audience;
        @JsonProperty("priority")
        private final Double priority;
        @JsonProperty("resource")
        private final ResourceContents resource;
        public EmbeddedResource(@JsonProperty("audience") List<Role> audience,
            @JsonProperty("priority") Double priority,
            @JsonProperty("resource") ResourceContents resource) {
            this.audience = audience;
            this.priority = priority;
            this.resource = resource;
        }
        public List<Role> getAudience() { return audience; }
        public Double getPriority() { return priority; }
        public ResourceContents getResource() { return resource; }
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Root {
        @JsonProperty("uri")
        private final String uri;
        @JsonProperty("name")
        private final String name;
        public Root(@JsonProperty("uri") String uri,
            @JsonProperty("name") String name) {
            this.uri = uri;
            this.name = name;
        }
        public String getUri() { return uri; }
        public String getName() { return name; }
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ListRootsResult {
        @JsonProperty("roots")
        private final List<Root> roots;
        public ListRootsResult(@JsonProperty("roots") List<Root> roots) {
            this.roots = roots;
        }
        public List<Root> getRoots() { return roots; }
    }
} 