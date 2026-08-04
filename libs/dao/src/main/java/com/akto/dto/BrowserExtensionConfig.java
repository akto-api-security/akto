package com.akto.dto;

import java.util.List;
import java.util.Map;

import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.bson.types.ObjectId;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class BrowserExtensionConfig {

    private ObjectId id;

    @BsonIgnore
    private String hexId;

    public static final String HOST = "host";
    private String host;

    public static final String PATHS = "paths";
    private List<String> paths;

    public static final String ACTIVE = "active";
    private boolean active;

    // ── config-driven monitoring fields (mirror the extension's monitoring-configs schema) ──
    // how the chat send travels: "http" | "websocket" | "graphql"
    public static final String TRANSPORT = "transport";
    private String transport;

    // HTTP method for http transport (POST/GET/…)
    public static final String METHOD = "method";
    private String method;

    // body decoder: json | form | sse | ws-frame | dgw | connect-rpc | socket.io | nested-envelope
    public static final String FORMAT = "format";
    private String format;

    // candidate paths to the user prompt in the request body (first match wins). Always stored as a
    // list (single paths are normalized to a 1-element list), so it reads through the pojo codec.
    public static final String PATH = "path";
    private List<String> path;

    // GraphQL only: operation names that carry a send
    public static final String OPERATIONS = "operations";
    private List<String> operations;

    // WebSocket only: key→value conditions selecting the prompt-bearing frame
    public static final String FRAME_MATCH = "frameMatch";
    private Map<String, String> frameMatch;

    // optional response/model extraction
    public static final String RESPONSE_FORMAT = "responseFormat";
    private String responseFormat;

    public static final String RESPONSE_PATH = "responsePath";
    private List<String> responsePath;

    public static final String MODEL_PATH = "modelPath";
    private List<String> modelPath;

    public static final String CREATED_BY = "createdBy";
    private String createdBy;

    public static final String UPDATED_BY = "updatedBy";
    private String updatedBy;

    public static final String CREATED_TIMESTAMP = "createdTimestamp";
    private int createdTimestamp;

    public static final String UPDATED_TIMESTAMP = "updatedTimestamp";
    private int updatedTimestamp;

    public BrowserExtensionConfig(String host, List<String> paths, boolean active,
                                  String createdBy, String updatedBy,
                                  int createdTimestamp, int updatedTimestamp) {
        this.host = host;
        this.paths = paths;
        this.active = active;
        this.createdBy = createdBy;
        this.updatedBy = updatedBy;
        this.createdTimestamp = createdTimestamp;
        this.updatedTimestamp = updatedTimestamp;
    }

    public String getHexId() {
        if (this.id != null) {
            return this.id.toHexString();
        }
        return null;
    }
}
