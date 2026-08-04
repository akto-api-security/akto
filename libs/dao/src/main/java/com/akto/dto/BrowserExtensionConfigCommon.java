package com.akto.dto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bson.Document;
import org.bson.types.ObjectId;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Config stored in the shared (common) DB, so one document applies across accounts.
 * Only {@link #host} is mandatory, every other field is optional.
 *
 * The collection is schema-loose (a host can be listed with paths only, or carry full
 * extraction details), and {@link #path} is intentionally polymorphic - a single json
 * path or a list of candidate paths - so documents are read as raw {@link Document}s and
 * mapped here instead of going through the pojo codec.
 */
@Getter
@Setter
@NoArgsConstructor
public class BrowserExtensionConfigCommon {

    // NONE: lombok must not generate getId() returning the raw ObjectId (it serializes as a
    // messy nested object). get_id() below emits it as the plain hex string under key `_id`.
    @Getter(AccessLevel.NONE)
    private ObjectId id;

    public static final String HOST = "host";
    private String host;

    public static final String ACTIVE = "active";
    // absent in the document means enabled
    private boolean active = true;

    public static final String PATHS = "paths";
    private List<String> paths;

    public static final String TRANSPORT = "transport";
    private String transport;

    public static final String METHOD = "method";
    private String method;

    public static final String OPERATIONS = "operations";
    private List<String> operations;

    public static final String FORMAT = "format";
    private String format;

    // either a String or a List<String> of candidate paths, mirrored as stored
    public static final String PATH = "path";
    private Object path;

    public static final String FRAME_MATCH = "frameMatch";
    private Map<String, Object> frameMatch;

    // where the logged-in user's identity/email is read from: { source, endpoint, emailPath }
    public static final String IDENTITY = "identity";
    private Map<String, Object> identity;

    public static final String RESPONSE_FORMAT = "responseFormat";
    private String responseFormat;

    // either a String or a List<String> of candidate paths, mirrored as stored
    public static final String RESPONSE_PATH = "responsePath";
    private Object responsePath;

    public static final String RESPONSE_PATHS = "responsePaths";
    private List<String> responsePaths;

    public static final String RESPONSE_KEY_PATH = "responseKeyPath";
    private String responseKeyPath;

    // either a String or a List<String> of candidate paths, mirrored as stored
    public static final String MODEL_PATH = "modelPath";
    private Object modelPath;

    // header-based model resolution: { name, index, map }
    public static final String MODEL_HEADER = "modelHeader";
    private Map<String, Object> modelHeader;

    public static final String TRIGGER_FRAME = "triggerFrame";
    private Map<String, Object> triggerFrame;

    // synthetic frames replayed after a blocked send; each entry is a raw frame object
    public static final String BLOCK_RESPONSE_FRAMES = "blockResponseFrames";
    private List<Object> blockResponseFrames;

    public static final String ENFORCE_AT = "enforceAt";
    private String enforceAt;

    // getter name is get_id() on purpose: the struts json serializer keys output off the bean
    // property, so this surfaces as `_id` (matching the db) with the same 24-char hex value as the
    // stored ObjectId. json has no native ObjectId, so the hex string is the faithful form.
    public String get_id() {
        if (this.id != null) {
            return this.id.toHexString();
        }
        return null;
    }

    /**
     * @return null if the document has no usable host, since host is the only mandatory field
     */
    public static BrowserExtensionConfigCommon fromDocument(Document doc) {
        if (doc == null) {
            return null;
        }

        String host = asString(doc.get(HOST));
        if (host == null || host.trim().isEmpty()) {
            return null;
        }

        BrowserExtensionConfigCommon config = new BrowserExtensionConfigCommon();
        config.host = host.trim();

        Object id = doc.get("_id");
        if (id instanceof ObjectId) {
            config.id = (ObjectId) id;
        }

        Object active = doc.get(ACTIVE);
        config.active = !(active instanceof Boolean) || (Boolean) active;

        config.paths = asStringList(doc.get(PATHS));
        config.operations = asStringList(doc.get(OPERATIONS));
        config.transport = asString(doc.get(TRANSPORT));
        config.method = asString(doc.get(METHOD));
        config.format = asString(doc.get(FORMAT));
        config.path = asPath(doc.get(PATH));
        config.frameMatch = asMap(doc.get(FRAME_MATCH));
        config.identity = asMap(doc.get(IDENTITY));
        config.responseFormat = asString(doc.get(RESPONSE_FORMAT));
        config.responsePath = asPath(doc.get(RESPONSE_PATH));
        config.responsePaths = asStringList(doc.get(RESPONSE_PATHS));
        config.responseKeyPath = asString(doc.get(RESPONSE_KEY_PATH));
        config.modelPath = asPath(doc.get(MODEL_PATH));
        config.modelHeader = asMap(doc.get(MODEL_HEADER));
        config.triggerFrame = asMap(doc.get(TRIGGER_FRAME));
        config.blockResponseFrames = asObjectList(doc.get(BLOCK_RESPONSE_FRAMES));
        config.enforceAt = asString(doc.get(ENFORCE_AT));

        return config;
    }

    /**
     * Effective config list for an account = the active common catalogue overlaid with the account's
     * own choices, keyed by host:
     *  - an active account config for a NEW host is added (a custom host)
     *  - an inactive account config removes that host (an opt-out of a common host)
     *  - a common host the account did not touch stays on
     * Common wins when both have the same host (putIfAbsent), so a same-host account row only opts
     * out or adds - it does not override the catalogue entry's fields.
     */
    public static List<BrowserExtensionConfigCommon> merge(
            List<BrowserExtensionConfigCommon> commonActive,
            List<BrowserExtensionConfigCommon> accountConfigs) {
        Map<String, BrowserExtensionConfigCommon> byHost = new LinkedHashMap<>();
        if (commonActive != null) {
            for (BrowserExtensionConfigCommon c : commonActive) {
                String key = hostKey(c);
                if (key != null) {
                    byHost.putIfAbsent(key, c);   // if the catalogue repeats a host, keep the first
                }
            }
        }
        if (accountConfigs != null) {
            for (BrowserExtensionConfigCommon a : accountConfigs) {
                String key = hostKey(a);
                if (key == null) {
                    continue;
                }
                if (a.isActive()) {
                    byHost.putIfAbsent(key, a);   // custom host; common entry (if any) wins
                } else {
                    byHost.remove(key);           // opt-out
                }
            }
        }
        return new ArrayList<>(byHost.values());
    }

    private static String hostKey(BrowserExtensionConfigCommon config) {
        if (config == null || config.host == null || config.host.trim().isEmpty()) {
            return null;
        }
        return config.host.trim().toLowerCase();
    }

    private static String asString(Object value) {
        return value instanceof String ? (String) value : null;
    }

    private static List<String> asStringList(Object value) {
        if (value instanceof String) {
            return new ArrayList<>(Collections.singletonList((String) value));
        }
        if (!(value instanceof List)) {
            return null;
        }
        List<String> result = new ArrayList<>();
        for (Object o : (List<?>) value) {
            if (o instanceof String) {
                result.add((String) o);
            }
        }
        return result;
    }

    // list of arbitrary objects (e.g. raw frame templates), mirrored as stored
    private static List<Object> asObjectList(Object value) {
        if (!(value instanceof List)) {
            return null;
        }
        return new ArrayList<>((List<?>) value);
    }

    // keeps the stored shape - a bare String stays a String, a list stays a list
    private static Object asPath(Object value) {
        if (value instanceof String) {
            return value;
        }
        return asStringList(value);
    }

    private static Map<String, Object> asMap(Object value) {
        if (!(value instanceof Map)) {
            return null;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
            if (entry.getKey() != null) {
                result.put(entry.getKey().toString(), entry.getValue());
            }
        }
        return result;
    }
}
