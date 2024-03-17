package com.akto.util;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class JsonStringPayloadModifier {
    private static final ObjectMapper mapper = new ObjectMapper();

    public static String jsonStringPayloadModifier(String data, String path, String newVal) throws Exception {

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
}
