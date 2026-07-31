package com.akto.dto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bson.Document;
import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.bson.types.ObjectId;

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

    private ObjectId id;

    @BsonIgnore
    private String hexId;

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

    public static final String TAG = "tag";
    private String tag;

    // stored as `icon_url` in the document, used as the host's icon
    public static final String ICON_URL = "icon_url";
    private String iconUrl;

    public String getHexId() {
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
        config.tag = asString(doc.get(TAG));
        config.iconUrl = asString(doc.get(ICON_URL));

        return config;
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
