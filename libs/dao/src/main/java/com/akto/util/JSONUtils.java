package com.akto.util;

import com.akto.dto.type.RequestTemplate;
import com.akto.util.modifier.PayloadModifier;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;

import java.io.IOException;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JSONUtils {

    private static final Logger logger = LoggerFactory.getLogger(JSONUtils.class);
    private static final ObjectMapper mapper = new ObjectMapper();
    private static void flatten(Object obj, String prefix, Map<String, Set<Object>> ret) {
        if (obj instanceof BasicDBObject) {
            BasicDBObject basicDBObject = (BasicDBObject) obj;

            Set<String> keySet = basicDBObject.keySet();

            if (prefix != null && !prefix.isEmpty() && (keySet == null || keySet.isEmpty())) {
                Set<Object> values = ret.getOrDefault(prefix, new HashSet<>());
                values.add(obj);
                ret.put(prefix, values);
            }

            for(String key: keySet) {

                if (key == null) {
                    continue;
                }
                boolean anyAlphabetExists = false;

                final int sz = key.length();
                for (int i = 0; i < sz; i++) {
                    final char nowChar = key.charAt(i);
                    if (Character.isLetter(nowChar)) {
                        anyAlphabetExists = true;
                        break;
                    }
                }

                key = anyAlphabetExists ? key: "NUMBER";
                Object value = basicDBObject.get(key);
                flatten(value, prefix + (prefix.isEmpty() ? "" : "#") + key, ret);
            }
        } else if (obj instanceof BasicDBList) {
            for(Object elem: (BasicDBList) obj) {
                flatten(elem, prefix+(prefix.isEmpty() ? "$" : "#$"), ret);
            }
        } else {
            Set<Object> values = ret.getOrDefault(prefix, new HashSet<>());
            values.add(obj);
            ret.put(prefix, values);
        }
    }

    public static Map<String, Set<Object>> flatten(BasicDBObject object) {
        Map<String, Set<Object>> ret = new HashMap<>();
        if (object == null) return ret;
        String prefix = "";
        flatten(object, prefix, ret);
        return ret;
    }

    private static final JsonFactory jsonFactory = new JsonFactory();

    /**
     * Stream-flattens a JSON string directly into a Map without building an intermediate
     * BasicDBObject tree. Produces the same output as flatten(BasicDBObject.parse(json)).
     *
     * Key format rules (matching existing flatten):
     * - Object keys separated by '#'
     * - Array elements use '$' (with '#$' if not at root)
     * - Keys with no letters replaced with "NUMBER"
     * - Leaf values stored in Set<Object>
     */
    private static final int MAX_ARRAY_ELEMENTS = 2;
    private static final Set<String> SKIP_ROOT_KEYS = new HashSet<>(Arrays.asList("extensions"));

    public static Map<String, Set<Object>> streamFlatten(String json) {
        Map<String, Set<Object>> ret = new HashMap<>();
        if (json == null || json.isEmpty()) return ret;

        try (com.fasterxml.jackson.core.JsonParser parser = jsonFactory.createParser(json)) {
            Deque<String> pathStack = new ArrayDeque<>();
            Deque<Boolean> arrayStack = new ArrayDeque<>();
            // Track element count for each array level
            Deque<Integer> arrayElementCountStack = new ArrayDeque<>();
            String pendingFieldName = null;
            int depth = 0;

            JsonToken token;
            while ((token = parser.nextToken()) != null) {
                switch (token) {
                    case START_OBJECT:
                        depth++;
                        // Check if this object is an array element that exceeds the limit
                        if (pendingFieldName == null && !arrayStack.isEmpty() && arrayStack.peek()) {
                            int count = arrayElementCountStack.peek();
                            arrayElementCountStack.poll();
                            arrayElementCountStack.push(count + 1);
                            if (count >= MAX_ARRAY_ELEMENTS) {
                                parser.skipChildren(); // skips entire object without tokenizing
                                depth--;
                                break;
                            }
                            pathStack.push("$");
                        } else if (pendingFieldName != null) {
                            // Skip root-level keys like "extensions" (GraphQL tracing)
                            if (depth == 2 && SKIP_ROOT_KEYS.contains(pendingFieldName)) {
                                parser.skipChildren();
                                pendingFieldName = null;
                                depth--;
                                break;
                            }
                            pathStack.push(pendingFieldName);
                            pendingFieldName = null;
                        }
                        arrayStack.push(false);
                        break;

                    case END_OBJECT:
                        depth--;
                        arrayStack.poll();
                        if (!pathStack.isEmpty()) {
                            pathStack.poll();
                        }
                        break;

                    case START_ARRAY:
                        depth++;
                        // Check if this array is itself an element of a parent array
                        if (pendingFieldName == null && !arrayStack.isEmpty() && arrayStack.peek()) {
                            int count = arrayElementCountStack.peek();
                            arrayElementCountStack.poll();
                            arrayElementCountStack.push(count + 1);
                            if (count >= MAX_ARRAY_ELEMENTS) {
                                parser.skipChildren();
                                depth--;
                                break;
                            }
                            pathStack.push("$");
                        } else if (pendingFieldName != null) {
                            // Skip root-level array keys like "extensions"
                            if (depth == 2 && SKIP_ROOT_KEYS.contains(pendingFieldName)) {
                                parser.skipChildren();
                                pendingFieldName = null;
                                depth--;
                                break;
                            }
                            pathStack.push(pendingFieldName);
                            pendingFieldName = null;
                        }
                        arrayStack.push(true);
                        arrayElementCountStack.push(0);
                        break;

                    case END_ARRAY:
                        depth--;
                        arrayStack.poll();
                        arrayElementCountStack.poll();
                        if (!pathStack.isEmpty()) {
                            pathStack.poll();
                        }
                        break;

                    case FIELD_NAME:
                        String fieldName = parser.getCurrentName();
                        if (fieldName != null && !hasLetter(fieldName)) {
                            fieldName = "NUMBER";
                        }
                        pendingFieldName = fieldName;
                        break;

                    // Leaf values
                    case VALUE_STRING:
                    case VALUE_NUMBER_INT:
                    case VALUE_NUMBER_FLOAT:
                    case VALUE_TRUE:
                    case VALUE_FALSE:
                    case VALUE_NULL:
                        // Check if this is a leaf inside an array that exceeds the limit
                        if (pendingFieldName == null && !arrayStack.isEmpty() && arrayStack.peek()) {
                            int count = arrayElementCountStack.peek();
                            arrayElementCountStack.poll();
                            arrayElementCountStack.push(count + 1);
                            if (count >= MAX_ARRAY_ELEMENTS) {
                                break; // skip this value
                            }
                        }
                        // Skip root-level scalar keys like "extensions"
                        if (pendingFieldName != null && depth == 1 && SKIP_ROOT_KEYS.contains(pendingFieldName)) {
                            pendingFieldName = null;
                            break;
                        }

                        String leafKey;
                        if (pendingFieldName != null) {
                            leafKey = buildPath(pathStack, pendingFieldName);
                            pendingFieldName = null;
                        } else if (!arrayStack.isEmpty() && arrayStack.peek()) {
                            leafKey = buildPath(pathStack, "$");
                        } else {
                            leafKey = buildPathFromStack(pathStack);
                        }

                        Object value = extractValue(parser, token);
                        ret.computeIfAbsent(leafKey, k -> new HashSet<>()).add(value);
                        break;

                    default:
                        break;
                }
            }
        } catch (IOException e) {
            logger.error("Error in streamFlatten", e);
        }

        return ret;
    }

    private static boolean hasLetter(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (Character.isLetter(s.charAt(i))) return true;
        }
        return false;
    }

    private static String buildPathFromStack(Deque<String> stack) {
        if (stack.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        // Stack is LIFO, iterate in reverse (bottom to top)
        Iterator<String> it = stack.descendingIterator();
        boolean first = true;
        while (it.hasNext()) {
            String segment = it.next();
            if (first) {
                if ("$".equals(segment)) {
                    sb.append("$");
                } else {
                    sb.append(segment);
                }
                first = false;
            } else {
                if ("$".equals(segment)) {
                    sb.append("#$");
                } else {
                    sb.append('#').append(segment);
                }
            }
        }
        return sb.toString();
    }

    private static String buildPath(Deque<String> stack, String leaf) {
        String base = buildPathFromStack(stack);
        if (base.isEmpty()) {
            return "$".equals(leaf) ? "$" : leaf;
        }
        if ("$".equals(leaf)) {
            return base + "#$";
        }
        return base + "#" + leaf;
    }

    private static Object extractValue(com.fasterxml.jackson.core.JsonParser parser, JsonToken token) throws IOException {
        switch (token) {
            case VALUE_STRING:
                return parser.getText();
            case VALUE_NUMBER_INT:
                return parser.getLongValue();
            case VALUE_NUMBER_FLOAT:
                return parser.getDoubleValue();
            case VALUE_TRUE:
                return true;
            case VALUE_FALSE:
                return false;
            case VALUE_NULL:
                return null;
            default:
                return null;
        }
    }

    public static BasicDBObject flattenWithDots(Object object) {
        BasicDBObject ret = new BasicDBObject();
        String prefix = "";
        flattenWithDots(object, prefix, ret);
        return ret;
    }

    private static void flattenWithDots(Object obj, String prefix, BasicDBObject ret) {
        if (obj instanceof BasicDBObject) {
            BasicDBObject basicDBObject = (BasicDBObject) obj;

            Set<String> keySet = basicDBObject.keySet();

            if (prefix != null && !prefix.isEmpty() && (keySet == null || keySet.isEmpty())) {
                ret.put(prefix, obj);
            }

            for(String key: keySet) {

                if (key == null) {
                    continue;
                }
                boolean anyAlphabetExists = false;

                final int sz = key.length();
                for (int i = 0; i < sz; i++) {
                    final char nowChar = key.charAt(i);
                    if (Character.isLetter(nowChar)) {
                        anyAlphabetExists = true;
                        break;
                    }
                }

                key = anyAlphabetExists ? key: "NUMBER";
                Object value = basicDBObject.get(key);
                flattenWithDots(value, prefix + (prefix == null || prefix.isEmpty() ? "" : ".") + key, ret);
            }
        } else if (obj instanceof BasicDBList) {
            int idx = 0;
            for(Object elem: (BasicDBList) obj) {
                flattenWithDots(elem, prefix+("["+idx+"]"), ret);
                idx += 1;
            }
        } else {
            ret.put(prefix, obj);
        }
    }

    public static String modify(String jsonBody, Set<String> values, PayloadModifier payloadModifier) {
        try {
            BasicDBObject payload = RequestTemplate.parseRequestPayload(jsonBody, null);
            if (payload.isEmpty()) return jsonBody;
            BasicDBObject modifiedPayload = modify(payload, values, payloadModifier);
            if (modifiedPayload.containsKey("json")) {
                return new Gson().toJson(modifiedPayload.get("json"));
            }
            return new Gson().toJson(modifiedPayload);
        } catch (Exception e) {
            return jsonBody;
        }
    }

    public static BasicDBObject modify(BasicDBObject obj, Set<String> values, PayloadModifier payloadModifier) {
        BasicDBObject result = (BasicDBObject) obj.copy();
        modify(result, "" ,values, payloadModifier);
        return result;
    }

    private static void modify(Object obj, String prefix, Set<String> values, PayloadModifier payloadModifier) {
        if (obj instanceof BasicDBObject) {
            BasicDBObject basicDBObject = (BasicDBObject) obj;
            Set<String> keySet = basicDBObject.keySet();

            for(String key: keySet) {
                if (key == null) continue;
                String fullKey = prefix + (prefix.isEmpty() ? "" : "#") + key;
                Object value = basicDBObject.get(key);
                if (values.contains(fullKey)) {
                    basicDBObject.put(key, payloadModifier.modify(fullKey, value));
                }
                modify(value, fullKey, values, payloadModifier);
            }

        } else if (obj instanceof BasicDBList) {
            for (Object elem: (BasicDBList) obj) {
                modify(elem, prefix+(prefix.isEmpty() ? "$" : "#$"), values, payloadModifier);
            }
        }
    }

    public static String parseIfJsonP(String payload) {
        if (payload == null) return null;
        if (!payload.startsWith("{") && !payload.startsWith("[") && !payload.startsWith("<")) {//candidate for json with padding handleRequest ({abc : abc})
            int indexOfMethodStart = payload.indexOf('(');
            int indexOfMethodEnd = payload.lastIndexOf(')');
            try {
                String nextChar = payload.substring(indexOfMethodStart + 1, indexOfMethodStart + 5);
                if (nextChar.startsWith("{")) {
                    String json = payload.substring(indexOfMethodStart + 1, indexOfMethodEnd);//Getting the content of method
                    com.google.gson.JsonParser.parseString(json);
                    return json;
                }
            }catch (Exception e) {
                return payload;
            }
        }
        return payload;
    }



    public static Map<String, List<String>> modifyHeaderValues(Map<String, List<String>> headers, PayloadModifier payloadModifier) {
        if (headers == null) return null;
        boolean flag = false;
        Map<String, List<String>> modifiedHeaders = new HashMap<>(headers);
        for (String header: modifiedHeaders.keySet()) {
            List<String> values = modifiedHeaders.get(header);
            List<String> newValues = new ArrayList<>();
            for (String value: values) {
                Object modifiedHeader = payloadModifier.modify(header, value);
                if (modifiedHeader != null) {
                    newValues.add(modifiedHeader.toString());
                    flag = true;
                } else {
                    newValues.add(value);
                }
            }
            modifiedHeaders.put(header, newValues);
        }

        if (!flag) return null;

        return modifiedHeaders;
    }


    public static <T> T fromJson(String json, Class<T> clazz) {
        try {
            return mapper.readValue(json, clazz);
        } catch (Exception e) {
            logger.error("Error while parsing JSON", e);
            return null;
        }
    }

    public static <T> T fromJson(String json, TypeReference<T> typeRef) {
        try {
            return mapper.readValue(json, typeRef);
        } catch (Exception e) {
            logger.error("Error while parsing JSON", e);
            return null;
        }
    }

    public static <T> T fromJson(Object object, TypeReference<T> typeRef) {
        try {
            return mapper.convertValue(object, typeRef);
        } catch (Exception e) {
            logger.error("Error while parsing JSON", e);
            return null;
        }
    }

    public static <T> T fromJson(Object object, Class<T> clazz) {
        try {
            return mapper.convertValue(object, clazz);
        } catch (Exception e) {
            logger.error("Error while parsing JSON", e);
            return null;
        }
    }

    public static Map<String, Object> getMap(String json) {
        return fromJson(json, new TypeReference<Map<String, Object>>() {});
    }

    public static Map<String, Object> getMap(Object object) {
        return fromJson(object, new TypeReference<Map<String, Object>>() {});
    }

    public static String getString(Object object) {
        try {
            return mapper.writeValueAsString(object);
        } catch (Exception e) {
            logger.error("Error while parsing JSON", e);
            return null;
        }
    }
}
