package com.akto.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.BasicDBObject;

public class JsonStringPayloadModifier {
    private static final ObjectMapper mapper = new ObjectMapper();

    public static String jsonStringPayloadModifier(String data, String path, String newVal) throws Exception {
        return jsonStringPayloadModifier(data, path, newVal, false);
    }

    public static String jsonStringPayloadModifier(String data, String path, String newVal, boolean createIfAbsent) throws Exception {
        if (createIfAbsent) {
            return jsonStringPayloadModifierCreateIfAbsent(data, path, newVal);
        }

        JsonNode origRequestNode = mapper.readValue(data, JsonNode.class);
        JsonNode node = origRequestNode;
        String[] keys = path.split("\\.");
        for (int i = 0; i < keys.length - 1; i++) {
            node = node.get(keys[i]);
            if (node == null) {
                throw new Exception("invalid key specified");
            }
        }

        BasicDBObject payload = parsePayload(data);
        Object parsedValue = parseEscapedValue(newVal);
        boolean isModified = com.akto.dto.test_editor.Util.modifyValueInPayload(payload, null, keys[keys.length - 1], parsedValue);
        if (isModified) {
            return payload.toString();
        }
        return data;
    }

    private static String jsonStringPayloadModifierCreateIfAbsent(String data, String path, String newVal) throws Exception {
        BasicDBObject payload = parsePayload(data);
        String[] keys = path.split("\\.");
        if (keys.length == 0 || keys[0].isEmpty()) {
            throw new Exception("invalid key specified");
        }

        BasicDBObject current = payload;
        for (int i = 0; i < keys.length - 1; i++) {
            String key = keys[i];
            Object next = current.get(key);
            if (next instanceof BasicDBObject) {
                current = (BasicDBObject) next;
            } else {
                BasicDBObject created = new BasicDBObject();
                current.put(key, created);
                current = created;
            }
        }

        current.put(keys[keys.length - 1], parseEscapedValue(newVal));
        return payload.toString();
    }

    private static BasicDBObject parsePayload(String data) {
        if (data == null || data.trim().isEmpty()) {
            return new BasicDBObject();
        }
        try {
            return BasicDBObject.parse(data);
        } catch (Exception e) {
            return new BasicDBObject();
        }
    }

    private static Object parseEscapedValue(String newVal) throws Exception {
        BasicDBObject newValueObj = BasicDBObject.parse("{\"value\":\"" + newVal + "\"}");
        return newValueObj.get("value");
    }
}
