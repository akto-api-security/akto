package com.akto.dto;

import java.util.List;

import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.bson.types.ObjectId;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A browser-extension setting, account-scoped, stored in the
 * {@code browser_extension_configs} collection and pushed to the extension.
 *
 * Two record types share this collection (discriminated by {@link #type}):
 *  - MONITOR (default, legacy): host + paths the extension should monitor/guardrail.
 *  - BLOCK: a website-blocking rule enforced at the network layer (declarativeNetRequest).
 *
 * Block-rule fields ({@link #matchType}, {@link #value}, {@link #action}) are only
 * meaningful when {@code type == BLOCK}; monitoring fields ({@link #host}, {@link #paths})
 * only when {@code type == MONITOR}. {@link #active} applies to both.
 */
@Getter
@Setter
@NoArgsConstructor
public class BrowserExtensionConfig {

    private ObjectId id;

    @BsonIgnore
    private String hexId;

    // Record type discriminator. Absent/empty is treated as MONITOR for backward compatibility.
    public static final String TYPE = "type";
    private String type;

    public static final String HOST = "host";
    private String host;

    public static final String PATHS = "paths";
    private List<String> paths;

    public static final String ACTIVE = "active";
    private boolean active;

    // ---- Block-rule fields (type == BLOCK) ----
    public static final String MATCH_TYPE = "matchType";
    private String matchType;

    public static final String VALUE = "value";
    private String value;

    public static final String ACTION = "action";
    private String action;

    public static final String DESCRIPTION = "description";
    private String description;

    public static final String CREATED_BY = "createdBy";
    private String createdBy;

    public static final String UPDATED_BY = "updatedBy";
    private String updatedBy;

    public static final String CREATED_TIMESTAMP = "createdTimestamp";
    private int createdTimestamp;

    public static final String UPDATED_TIMESTAMP = "updatedTimestamp";
    private int updatedTimestamp;

    // type values
    public static final String TYPE_MONITOR = "MONITOR";
    public static final String TYPE_BLOCK = "BLOCK";

    // matchType values (type == BLOCK)
    public static final String MATCH_TYPE_DOMAIN = "DOMAIN";       // domain + all subdomains + paths
    public static final String MATCH_TYPE_HOST = "HOST";           // exact host only; siblings unaffected
    public static final String MATCH_TYPE_URL_PATTERN = "URL_PATTERN"; // wildcard URL/path match
    public static final String MATCH_TYPE_REGEX = "REGEX";         // full regex over the URL

    // action values (type == BLOCK)
    public static final String ACTION_BLOCK = "block";
    public static final String ACTION_ALLOW = "allow"; // exception that overrides a broader block

    public BrowserExtensionConfig(String host, List<String> paths, boolean active,
                                  String createdBy, String updatedBy,
                                  int createdTimestamp, int updatedTimestamp) {
        this.type = TYPE_MONITOR;
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
