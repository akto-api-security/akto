package com.akto.runtime;

import com.akto.dto.HttpResponseParams;
import com.akto.dto.settings.DefaultPayload;
import com.akto.dto.type.SingleTypeInfo.SuperType;
import com.akto.dto.type.URLMethods.Method;
import com.akto.dto.type.URLTemplate;
import com.akto.log.LoggerMaker;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

import java.net.URL;
import java.util.*;

public class RuntimeUtil {

    public static final String JUICE_SHOP_DEMO_COLLECTION_NAME = "juice_shop_demo";
    public static final String VULNERABLE_API_COLLECTION_NAME = "vulnerable_apis";
    public static final String LLM_API_COLLECTION_NAME = "llm_apis";
    public static final int VULNERABLE_API_COLLECTION_ID = 1111111111;
    public static final int LLM_API_COLLECTION_ID = 1222222222;

    private static final LoggerMaker loggerMaker = new LoggerMaker(RuntimeUtil.class);
    public static boolean matchesDefaultPayload(HttpResponseParams httpResponseParams, Map<String, DefaultPayload> defaultPayloadMap) {
        try {
            Map<String, List<String>> reqHeaders = httpResponseParams.getRequestParams().getHeaders();
            List<String> host = reqHeaders.getOrDefault("host", new ArrayList<>());

            String testHost = "";
            if (host != null && !host.isEmpty() && host.get(0) != null) {
                testHost = host.get(0);
            } else {
                String urlStr = httpResponseParams.getRequestParams().getURL();
                URL url = new URL(urlStr);
                testHost = url.getHost();
            }

            testHost = Base64.getEncoder().encodeToString(testHost.getBytes());

            DefaultPayload defaultPayload = defaultPayloadMap.get(testHost);
            if (defaultPayload != null && defaultPayload.getRegexPattern().matcher(httpResponseParams.getPayload().replaceAll("\n", "")).matches()) {
                return true;
            }

        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error while filtering default payloads: " + e.getMessage(), LoggerMaker.LogDb.RUNTIME);
        }

        return false;
    }

    public static boolean hasSpecialCharacters(String input) {
        // Define the special characters
        String specialCharacters = "<>%/?#[]@!$&'()*+,;=";
        for (char c : input.toCharArray()) {
            if (specialCharacters.contains(Character.toString(c))) {
                return true;
            }
        }

        return false;
    }

    private static String trim(String url) {
        if (url.startsWith("/")) url = url.substring(1, url.length());
        if (url.endsWith("/")) url = url.substring(0, url.length()-1);
        return url;
    }

    private static String[] trimAndSplit(String url) {
        return trim(url).split("/");
    }

    public static URLTemplate createUrlTemplate(String url, Method method) {
        String[] tokens = trimAndSplit(url);
        SuperType[] types = new SuperType[tokens.length];
        for(int i = 0; i < tokens.length; i ++ ) {
            String token = tokens[i];

            if (token.equals(SuperType.STRING.name())) {
                tokens[i] = null;
                types[i] = SuperType.STRING;
            } else if (token.equals(SuperType.INTEGER.name())) {
                tokens[i] = null;
                types[i] = SuperType.INTEGER;
            } else if (token.equals(SuperType.OBJECT_ID.name())) {
                tokens[i] = null;
                types[i] = SuperType.OBJECT_ID;
            } else if (token.equals(SuperType.FLOAT.name())) {
                tokens[i] = null;
                types[i] = SuperType.FLOAT;
            } else {
                types[i] = null;
            }

        }

        URLTemplate urlTemplate = new URLTemplate(tokens, types, method);

        return urlTemplate;
    }

    static ObjectMapper mapper = new ObjectMapper();
    static JsonFactory factory = mapper.getFactory();

    public static void extractAllValuesFromPayload(String payload, Map<String,Set<String>> payloadMap) throws Exception{
        JsonParser jp = factory.createParser(payload);
        JsonNode node = mapper.readTree(jp);
        extractAllValuesFromPayload(node,new ArrayList<>(),payloadMap);
    }

    public static void extractAllValuesFromPayload(JsonNode node, List<String> params, Map<String, Set<String>> values) {
        // TODO: null values remove
        if (node == null) return;
        if (node.isValueNode()) {
            String textValue = node.asText();
            if (textValue != null) {
                String param = String.join("",params);
                if (param.startsWith("#")) {
                    param = param.substring(1);
                }
                if (!values.containsKey(param)) {
                    values.put(param, new HashSet<>());
                }
                values.get(param).add(textValue);
            }
        } else if (node.isArray()) {
            ArrayNode arrayNode = (ArrayNode) node;
            for(int i = 0; i < arrayNode.size(); i++) {
                JsonNode arrayElement = arrayNode.get(i);
                params.add("#$");
                extractAllValuesFromPayload(arrayElement, params, values);
                params.remove(params.size()-1);
            }
        } else {
            Iterator<String> fieldNames = node.fieldNames();
            while(fieldNames.hasNext()) {
                String fieldName = fieldNames.next();
                params.add("#"+fieldName);
                JsonNode fieldValue = node.get(fieldName);
                extractAllValuesFromPayload(fieldValue, params,values);
                params.remove(params.size()-1);
            }
        }

    }
}
