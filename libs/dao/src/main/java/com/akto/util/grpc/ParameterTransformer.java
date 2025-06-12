package com.akto.util.grpc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.akto.dao.CodeAnalysisSingleTypeInfoDao;
import com.akto.dao.context.Context;
import com.akto.dto.ApiInfo;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.dependency_flow.KVPair;
import com.akto.dto.dependency_flow.KVPair.KVType;
import com.akto.dto.traffic.Key;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;

public class ParameterTransformer {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static List<String> createSampleUsingReplaceDetails(Key id, List<KVPair> keyValue){
        List<ApiInfoKey> apiInfoKeys = new ArrayList<>();
        apiInfoKeys.add(new ApiInfo.ApiInfoKey(id.getApiCollectionId(), id.getUrl(), id.getMethod()));
        Map<ApiInfoKey, List<String>> parametersMap = CodeAnalysisSingleTypeInfoDao.instance.fetchRequestParameters(apiInfoKeys);
        List<String> samples = new ArrayList<>();
        for(ApiInfoKey apiInfoKey: parametersMap.keySet()){
            List<String> params = parametersMap.get(apiInfoKey);
            Map<String, Object> paramValueMap = new HashMap<>();
            for (String param : params) {
                Object value = null;
                for (KVPair kv : keyValue) {
                    if (kv.getKey().equals(param)) {
                        if (KVType.INTEGER.equals(kv.getType())) {
                            value = Integer.parseInt(kv.getValue());
                        } else if (KVType.STRING.equals(kv.getType())) {
                            value = kv.getValue();
                        } else if (KVType.BOOLEAN.equals(kv.getType())) {
                            value = Boolean.valueOf(kv.getValue());
                        }
                        continue;
                    }
                }
                if (value != null) {
                    paramValueMap.put(transformKey(param), value);
                }
            }
            JsonNode requestJsonNode = transform(paramValueMap);

            Map<String, Object> sampleJson = new HashMap<>();
            sampleJson.put("destIp", "");
            sampleJson.put("method", id.getMethod().name());
            sampleJson.put("requestPayload", requestJsonNode.toString());
            sampleJson.put("responsePayload", "{}");
            sampleJson.put("ip", "");
            sampleJson.put("source", "HAR");
            sampleJson.put("type", "HTTP/1.1");
            sampleJson.put("path", id.getUrl());
            sampleJson.put("requestHeaders", "{}");
            sampleJson.put("responseHeaders", "{}");
            sampleJson.put("time", Context.now()+"");
            sampleJson.put("statusCode", "200");
            sampleJson.put("akto_account_id", Context.accountId.get());
            try{
                samples.add(objectMapper.writeValueAsString(sampleJson));
            } catch (Exception e){
            }
        }
        return samples;
    }

    public static JsonNode transform(Map<String, Object> params) {
        ObjectNode root = objectMapper.createObjectNode();
        
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            String[] parts = entry.getKey().split("#");
            processPath(root, parts, 0, entry.getValue());
        }
        
        return root;
    }
    
    private static void processPath(JsonNode current, String[] parts, int index, Object value) {
        if (index == parts.length - 1) {
            if (value instanceof Integer) {
                ((ObjectNode) current).put(parts[index], (Integer) value);
            } else if(value instanceof Boolean){
                ((ObjectNode) current).put(parts[index], (Boolean) value);
            } else {
                // Default string
                ((ObjectNode) current).put(parts[index], (String) value);
            }
            return;
        }
        
        String part = parts[index];
        // Check if next part is array marker
        boolean nextIsArray = (index + 1 < parts.length && parts[index + 1].equals("$"));

        if (nextIsArray) {
            ArrayNode arrayNode;
            if (current.has(parts[index]) && current.get(parts[index]).isArray()) {
                arrayNode = (ArrayNode) current.get(parts[index]);
            } else {
                arrayNode = objectMapper.createArrayNode();
                ((ObjectNode) current).set(parts[index], arrayNode);
            }
            
            ObjectNode targetNode;
            if (arrayNode.size() > 0 && arrayNode.get(arrayNode.size() - 1).isObject()) {
                // Merge into last object if not already set for this path
                targetNode = (ObjectNode) arrayNode.get(arrayNode.size() - 1);
            } else {
                targetNode = objectMapper.createObjectNode();
                arrayNode.add(targetNode);
            }
            // Skip the next part (the $ symbol) in recursive call
            processPath(targetNode, parts, index + 2, value);
        } else {
            
            ObjectNode nextNode;
            if (current.has(part) && current.get(part).isObject()) {
                nextNode = (ObjectNode) current.get(part);
            } else {
                nextNode = objectMapper.createObjectNode();
                ((ObjectNode) current).set(part, nextNode);
            }
            
            processPath(nextNode, parts, index + 1, value);
        }
    }

    public static String transformKey(String input) {
        StringBuilder result = new StringBuilder();
        int i = 0;
        
        while (i < input.length()) {
            char currentChar = input.charAt(i);
            
            // Check if we're at a potential type indicator
            if (currentChar == '$' && i < input.length() - 1) {
                // Look ahead for digits
                int j = i + 1;
                while (j < input.length() && Character.isDigit(input.charAt(j))) {
                    j++;
                }
                
                // If we found digits after $, skip both $ and digits
                if (j > i + 1) {
                    i = j;
                    continue;
                }
            }
            
            // Add the current character to result
            result.append(currentChar);
            i++;
        }
        
        return result.toString();
    }
}