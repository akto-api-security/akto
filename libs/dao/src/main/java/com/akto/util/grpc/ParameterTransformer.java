package com.akto.util.grpc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;

public class ParameterTransformer {
    private static final ObjectMapper objectMapper = new ObjectMapper();

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
            
            ObjectNode newNode = objectMapper.createObjectNode();
            arrayNode.add(newNode);
            // Skip the next part (the $ symbol) in recursive call
            processPath(newNode, parts, index + 2, value);
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

    public static void main(String[] args) {
        Map<String, Object> params = new HashMap<>();
        
        // Test cases
        params.put("a#b", "value1");
        params.put("a#c#$#d", "value2");
        params.put("x#y#$#z", "value3");
        params.put("x#y#$#z", "value4");
        params.put("p#q#r#$#s#t", "value5");
        params.put("p#q#r#$#s#q#$#w", "value5");
        params.put("m#n", "value6");
        params.put("m#o", "value7");
        
        JsonNode result = transform(params);
        
        try {
            // Pretty print the JSON
            String prettyJson = result.toString();
            System.out.println("Full JSON Structure:");
            System.out.println(prettyJson);
            
        } catch (Exception e) {
            e.printStackTrace();
        }

        String[] tests = {
            "keys$1#$#delegatable_contract_id$8#shardNum$2",
            "simple#key",
            "object$3#nested$2#field$1",
            "array#$#element$5",
            "$1#startsWith#type",
            "ends#with#type$8",
            "multiple$1#$#types$2#in$3#one$4#key"
        };

        for (String test : tests) {
            String transformed = transformKey(test);
            System.out.println("Original:    " + test);
            System.out.println("Transformed: " + transformed);
            System.out.println();
        }

    }
}