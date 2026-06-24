package com.akto.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class JsonStringPayloadModifier {
    private static final ObjectMapper mapper = new ObjectMapper();

    public static String jsonStringPayloadModifier(String data, String path, String newVal) throws Exception {
        return jsonStringPayloadModifier(data, path, newVal, false);
    }

    public static String jsonStringPayloadModifier(String data, String path, String newVal, boolean createIfAbsent) throws Exception {
        if (createIfAbsent) {
            return jsonStringPayloadModifierCreateIfAbsent(data, path, newVal);
        }

        try {
            JsonNode origRequestNode = mapper.readValue(data, JsonNode.class);
            JsonNode node = origRequestNode;
            JsonNode parentNode = origRequestNode;
            String []keys = path.split("\\.");
            for (int i=0; i<keys.length - 1; i++) {
                node = node.get(keys[i]);
                parentNode = node;
                if (node == null) {
                    throw new Exception("invalid key specified");
                }
            }
            if (node.get(keys[keys.length-1]) == null || !node.get(keys[keys.length-1]).isValueNode()) {
                throw new Exception("key not found in request payload");
            }
            
            if (newVal == null || newVal == "null") {
                ((ObjectNode) parentNode).remove(keys[keys.length-1]);
            } else {
                ((ObjectNode) parentNode).put(keys[keys.length-1], newVal);
            }
            return origRequestNode.toString();

        } catch (Exception e) {
            throw e;
        }
    }

    private static String jsonStringPayloadModifierCreateIfAbsent(String data, String path, String newVal) throws Exception {
        JsonNode root;
        if (data == null || data.trim().isEmpty()) {
            root = mapper.createObjectNode();
        } else {
            root = mapper.readTree(data);
            if (!root.isObject()) {
                root = mapper.createObjectNode();
            }
        }

        ObjectNode current = (ObjectNode) root;
        String[] keys = path.split("\\.");
        if (keys.length == 0 || keys[0].isEmpty()) {
            throw new Exception("invalid key specified");
        }

        for (int i = 0; i < keys.length - 1; i++) {
            JsonNode next = current.get(keys[i]);
            if (next == null || !next.isObject()) {
                ObjectNode created = mapper.createObjectNode();
                current.set(keys[i], created);
                current = created;
            } else {
                current = (ObjectNode) next;
            }
        }

        current.put(keys[keys.length - 1], newVal);
        return root.toString();
    }
}
