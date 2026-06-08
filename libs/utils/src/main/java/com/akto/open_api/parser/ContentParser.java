package com.akto.open_api.parser;

import java.net.URLEncoder;
import java.util.*;

import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.Pair;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import io.swagger.oas.inflector.examples.ExampleBuilder;
import io.swagger.oas.inflector.examples.XmlExampleSerializer;
import io.swagger.oas.inflector.examples.models.Example;
import io.swagger.oas.inflector.processors.JsonNodeExampleSerializer;
import io.swagger.util.Json;
import io.swagger.v3.core.util.Yaml;
import io.swagger.v3.oas.models.media.*;

public class ContentParser {

    private static final LoggerMaker loggerMaker = new LoggerMaker(ContentParser.class, LogDb.DASHBOARD);
    private final static Gson gson = new Gson();

    public static Pair<String, String> getExampleFromContent(Content content) {
        String ret = "";
        String contentType = "";
        Set<String> contentTypes = content.keySet();

        if (contentTypes != null && !contentTypes.isEmpty()) {
            // using first content type only.
            contentType = contentTypes.iterator().next();
            MediaType mediaType = content.get(contentType);

            // add cases for encoding object for multipart, media data.

            if (mediaType.getExample() != null) {
                ret = mediaType.getExample().toString();
            } else if (mediaType.getExamples() != null) {
                for (String exampleName : mediaType.getExamples().keySet()) {
                    ret = mediaType.getExamples().get(exampleName).getValue().toString();
                }
            } else {
                Schema<?> schema = mediaType.getSchema();

                // OpenAPI 3.1 uses JsonSchema which ExampleBuilder can't handle via instanceof.
                // Convert to typed subclasses so ExampleBuilder works.
                if (schema instanceof JsonSchema) {
                    schema = convertToTypedSchema(schema, new HashSet<>());
                }

                Example example = ExampleBuilder.fromSchema(schema, null);

                if (example != null) {

                    SimpleModule simpleModule = new SimpleModule().addSerializer(new JsonNodeExampleSerializer());
                    Json.mapper().registerModule(simpleModule);
                    Yaml.mapper().registerModule(simpleModule);

                    if(contentType.contains("xml")) {
                        ret = new XmlExampleSerializer().serialize(example);
                    } else if (contentType.contains("yaml")){
                        try {
                            ret = Yaml.pretty().writeValueAsString(example);
                        } catch (JsonProcessingException e) {
                            loggerMaker.infoAndAddToDb("Error while parsing yaml " + e.toString());
                        }
                    } else if (contentType.contains("x-www-form-urlencoded")){
                        try {
                            Map<String, Object> json = gson.fromJson(Json.pretty(example), new TypeToken<Map<String, Object>>() {}.getType());
                            StringBuilder sb = new StringBuilder();
                            for (String key : json.keySet()) {
                                if (sb.length() > 0) {
                                    sb.append("&");
                                }
                                sb.append(key).append("=").append(json.get(key));
                            }
                            ret = URLEncoder.encode(sb.toString(), "UTF-8");
                        } catch (Exception e) {
                            loggerMaker.infoAndAddToDb("Error while parsing x-www-form-urlencoded " + e.toString());
                        }
                    } else {
                        ret = Json.pretty(example);
                    }
                }

                // Guard against "null" or invalid serialization from ExampleBuilder
                if (ret == null || "null".equals(ret.trim())) {
                    ret = "";
                }

                // Fallback: if ExampleBuilder returned nothing, generate directly
                if (ret.isEmpty() && mediaType.getSchema() instanceof JsonSchema) {
                    Object generated = generateExample(mediaType.getSchema(), new HashSet<>());
                    if (generated != null) {
                        ret = gson.toJson(generated);
                    }
                }
            }
        }
        return new Pair<String,String>(contentType, ret);
    }

    /**
     * Convert OpenAPI 3.1 JsonSchema to typed subclasses that ExampleBuilder understands.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Schema<?> convertToTypedSchema(Schema<?> schema, Set<String> visited) {
        if (schema == null) return null;

        // Cycle detection using identity
        String identity = System.identityHashCode(schema) + "";
        if (visited.contains(identity)) return schema;
        visited.add(identity);

        // Only convert JsonSchema instances
        if (!(schema instanceof JsonSchema)) return schema;

        String type = resolveType(schema);
        Schema<?> converted;

        if ("string".equals(type)) {
            StringSchema ss = new StringSchema();
            copyCommonProperties(schema, ss);
            converted = ss;
        } else if ("integer".equals(type)) {
            IntegerSchema is = new IntegerSchema();
            copyCommonProperties(schema, is);
            converted = is;
        } else if ("number".equals(type)) {
            NumberSchema ns = new NumberSchema();
            copyCommonProperties(schema, ns);
            converted = ns;
        } else if ("boolean".equals(type)) {
            BooleanSchema bs = new BooleanSchema();
            copyCommonProperties(schema, bs);
            converted = bs;
        } else if ("array".equals(type)) {
            ArraySchema as = new ArraySchema();
            copyCommonProperties(schema, as);
            if (schema.getItems() != null) {
                as.setItems(convertToTypedSchema(schema.getItems(), visited));
            }
            converted = as;
        } else if ("object".equals(type) || schema.getProperties() != null) {
            ObjectSchema os = new ObjectSchema();
            copyCommonProperties(schema, os);
            if (schema.getProperties() != null) {
                Map<String, Schema> convertedProps = new LinkedHashMap<>();
                for (Map.Entry<String, Schema> entry : schema.getProperties().entrySet()) {
                    convertedProps.put(entry.getKey(), convertToTypedSchema(entry.getValue(), visited));
                }
                os.setProperties(convertedProps);
            }
            converted = os;
        } else if (schema.getAnyOf() != null || schema.getOneOf() != null || schema.getAllOf() != null) {
            // ComposedSchema for anyOf/oneOf/allOf
            ComposedSchema cs = new ComposedSchema();
            copyCommonProperties(schema, cs);
            if (schema.getAllOf() != null) {
                List<Schema> convertedList = new ArrayList<>();
                for (Schema s : schema.getAllOf()) {
                    convertedList.add(convertToTypedSchema(s, visited));
                }
                cs.setAllOf(convertedList);
            }
            if (schema.getAnyOf() != null) {
                List<Schema> convertedList = new ArrayList<>();
                for (Schema s : schema.getAnyOf()) {
                    // Skip null-type entries (FastAPI nullable pattern)
                    String sType = resolveType(s);
                    if ("null".equals(sType)) continue;
                    convertedList.add(convertToTypedSchema(s, visited));
                }
                cs.setAnyOf(convertedList);
            }
            if (schema.getOneOf() != null) {
                List<Schema> convertedList = new ArrayList<>();
                for (Schema s : schema.getOneOf()) {
                    String sType = resolveType(s);
                    if ("null".equals(sType)) continue;
                    convertedList.add(convertToTypedSchema(s, visited));
                }
                cs.setOneOf(convertedList);
            }
            converted = cs;
        } else {
            // Unknown type — return as-is
            return schema;
        }

        return converted;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void copyCommonProperties(Schema<?> from, Schema<?> to) {
        to.setDescription(from.getDescription());
        to.setFormat(from.getFormat());
        to.setExample(from.getExample());
        to.setEnum((List) from.getEnum());
        to.setDefault(from.getDefault());
        to.setNullable(from.getNullable());
        to.setReadOnly(from.getReadOnly());
        to.setWriteOnly(from.getWriteOnly());
        to.setRequired(from.getRequired());
        to.setTitle(from.getTitle());
        to.setMaximum(from.getMaximum());
        to.setMinimum(from.getMinimum());
        to.setMaxLength(from.getMaxLength());
        to.setMinLength(from.getMinLength());
        to.setPattern(from.getPattern());
    }

    /**
     * Fallback example generator for when ExampleBuilder still can't handle the schema.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object generateExample(Schema<?> schema, Set<String> visited) {
        if (schema == null) return null;

        String ref = schema.get$ref();
        if (ref != null) {
            if (visited.contains(ref)) return null;
            visited.add(ref);
        }

        if (schema.getExample() != null) return schema.getExample();

        List enumValues = schema.getEnum();
        if (enumValues != null && !enumValues.isEmpty()) return enumValues.get(0);

        List<Schema> anyOf = schema.getAnyOf();
        if (anyOf != null && !anyOf.isEmpty()) {
            for (Schema<?> option : anyOf) {
                String optType = resolveType(option);
                if ("null".equals(optType)) continue;
                Object result = generateExample(option, new HashSet<>(visited));
                if (result != null) return result;
            }
        }

        List<Schema> oneOf = schema.getOneOf();
        if (oneOf != null && !oneOf.isEmpty()) {
            for (Schema<?> option : oneOf) {
                String optType = resolveType(option);
                if ("null".equals(optType)) continue;
                Object result = generateExample(option, new HashSet<>(visited));
                if (result != null) return result;
            }
        }

        List<Schema> allOf = schema.getAllOf();
        if (allOf != null && !allOf.isEmpty()) {
            Map<String, Object> merged = new LinkedHashMap<>();
            for (Schema<?> part : allOf) {
                Object partExample = generateExample(part, new HashSet<>(visited));
                if (partExample instanceof Map) {
                    merged.putAll((Map<String, Object>) partExample);
                }
            }
            if (!merged.isEmpty()) return merged;
        }

        String type = resolveType(schema);

        Map<String, Schema> properties = schema.getProperties();
        if ("object".equals(type) || (properties != null && !properties.isEmpty())) {
            Map<String, Object> obj = new LinkedHashMap<>();
            if (properties != null) {
                for (Map.Entry<String, Schema> entry : properties.entrySet()) {
                    Object val = generateExample(entry.getValue(), new HashSet<>(visited));
                    obj.put(entry.getKey(), val != null ? val : "");
                }
            }
            return obj;
        }

        if ("array".equals(type)) {
            Schema<?> items = schema.getItems();
            if (items != null) {
                Object itemExample = generateExample(items, new HashSet<>(visited));
                List<Object> arr = new ArrayList<>();
                if (itemExample != null) arr.add(itemExample);
                return arr;
            }
            return new ArrayList<>();
        }

        if ("string".equals(type)) {
            String format = schema.getFormat();
            if ("date".equals(format)) return "2024-01-01";
            if ("date-time".equals(format)) return "2024-01-01T00:00:00Z";
            if ("email".equals(format)) return "user@example.com";
            if ("uri".equals(format) || "url".equals(format)) return "https://example.com";
            if ("uuid".equals(format)) return "550e8400-e29b-41d4-a716-446655440000";
            return "string";
        }
        if ("integer".equals(type)) return 0;
        if ("number".equals(type)) return 0.0;
        if ("boolean".equals(type)) return true;

        return null;
    }

    private static String resolveType(Schema<?> schema) {
        String type = schema.getType();
        if (type != null) return type;

        Set<String> types = schema.getTypes();
        if (types != null && !types.isEmpty()) {
            for (String t : types) {
                if (!"null".equals(t)) return t;
            }
            return "null";
        }
        return null;
    }

}
