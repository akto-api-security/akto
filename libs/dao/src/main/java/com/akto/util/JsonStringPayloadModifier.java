package com.akto.util;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.BasicDBObject;

public class JsonStringPayloadModifier {
    private static final ObjectMapper mapper = new ObjectMapper();
    public static String jsonStringPayloadModifier(String data, String path, String newVal) throws Exception {

        try {
            JsonNode origRequestNode = mapper.readValue(data, JsonNode.class);
            JsonNode node = origRequestNode;
            String []keys = path.split("\\.");
            for (int i=0; i<keys.length - 1; i++) {
                node = node.get(keys[i]);
                if (node == null) {
                    throw new Exception("invalid key specified");
                }
            }

            BasicDBObject payload;
            try {
                payload = BasicDBObject.parse(data);
            } catch (Exception e) {
                payload = new BasicDBObject();
            }

            BasicDBObject newValueObj = BasicDBObject.parse("{\"value\":\"" + newVal + "\"}");
            boolean isModified = com.akto.dto.test_editor.Util.modifyValueInPayload(payload, null, keys[keys.length - 1],newValueObj.get("value"));
            if(isModified){
                return payload.toString();
            }else{
                return data;
            }

        } catch (Exception e) {
            throw e;
        }
    }   
}
