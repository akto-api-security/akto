package com.akto.open_api.parser;

import static org.junit.Assert.*;

import java.util.Map;

import org.junit.Test;

import com.akto.util.Pair;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.parser.core.models.ParseOptions;

public class ContentParserTest {

    private static final Gson gson = new Gson();

    // ========================
    // OpenAPI 3.0 tests
    // ========================

    @Test
    public void testOpenApi30_ObjectWithProperties() {
        // Simulate OpenAPI 3.0 object schema with properties
        Content content = new Content();
        MediaType mt = new MediaType();
        ObjectSchema schema = new ObjectSchema();
        schema.addProperty("name", new StringSchema());
        schema.addProperty("age", new IntegerSchema());
        schema.addProperty("active", new BooleanSchema());
        mt.setSchema(schema);
        content.addMediaType("application/json", mt);

        Pair<String, String> result = ContentParser.getExampleFromContent(content);
        assertEquals("application/json", result.getFirst());
        assertNotNull(result.getSecond());
        assertFalse(result.getSecond().isEmpty());

        // Should contain all properties
        String json = result.getSecond();
        assertTrue("Should contain 'name'", json.contains("name"));
        assertTrue("Should contain 'age'", json.contains("age"));
        assertTrue("Should contain 'active'", json.contains("active"));
        System.out.println("[3.0 Object] " + json);
    }

    @Test
    public void testOpenApi30_StringSchema() {
        Content content = new Content();
        MediaType mt = new MediaType();
        mt.setSchema(new StringSchema());
        content.addMediaType("application/json", mt);

        Pair<String, String> result = ContentParser.getExampleFromContent(content);
        assertNotNull(result.getSecond());
        assertFalse(result.getSecond().isEmpty());
        System.out.println("[3.0 String] " + result.getSecond());
    }

    @Test
    public void testOpenApi30_ArraySchema() {
        Content content = new Content();
        MediaType mt = new MediaType();
        ArraySchema arr = new ArraySchema();
        arr.setItems(new StringSchema());
        mt.setSchema(arr);
        content.addMediaType("application/json", mt);

        Pair<String, String> result = ContentParser.getExampleFromContent(content);
        assertNotNull(result.getSecond());
        assertFalse(result.getSecond().isEmpty());
        assertTrue("Should contain array", result.getSecond().contains("["));
        System.out.println("[3.0 Array] " + result.getSecond());
    }

    @Test
    public void testOpenApi30_ComposedSchema() {
        Content content = new Content();
        MediaType mt = new MediaType();
        ComposedSchema cs = new ComposedSchema();
        ObjectSchema part1 = new ObjectSchema();
        part1.addProperty("id", new IntegerSchema());
        ObjectSchema part2 = new ObjectSchema();
        part2.addProperty("label", new StringSchema());
        cs.addAllOfItem(part1);
        cs.addAllOfItem(part2);
        mt.setSchema(cs);
        content.addMediaType("application/json", mt);

        Pair<String, String> result = ContentParser.getExampleFromContent(content);
        assertNotNull(result.getSecond());
        assertFalse(result.getSecond().isEmpty());
        System.out.println("[3.0 Composed] " + result.getSecond());
    }

    @Test
    public void testOpenApi30_EmptySchema() {
        // Empty schema — should not crash, should return empty or {}
        Content content = new Content();
        MediaType mt = new MediaType();
        mt.setSchema(new Schema<>());
        content.addMediaType("application/json", mt);

        Pair<String, String> result = ContentParser.getExampleFromContent(content);
        assertNotNull(result.getSecond());
        // Should not be "null"
        assertNotEquals("null", result.getSecond().trim());
        System.out.println("[3.0 Empty] '" + result.getSecond() + "'");
    }

    @Test
    public void testOpenApi30_NullSchema() {
        Content content = new Content();
        MediaType mt = new MediaType();
        mt.setSchema(null);
        content.addMediaType("application/json", mt);

        Pair<String, String> result = ContentParser.getExampleFromContent(content);
        assertNotNull(result.getSecond());
        System.out.println("[3.0 Null schema] '" + result.getSecond() + "'");
    }

    // ========================
    // OpenAPI 3.1 (JsonSchema) tests
    // ========================

    @Test
    public void testOpenApi31_ObjectWithProperties() {
        Content content = new Content();
        MediaType mt = new MediaType();

        JsonSchema schema = new JsonSchema();
        schema.setTypes(new java.util.LinkedHashSet<>(java.util.Arrays.asList("object")));

        JsonSchema nameProp = new JsonSchema();
        nameProp.setTypes(new java.util.LinkedHashSet<>(java.util.Arrays.asList("string")));

        JsonSchema ageProp = new JsonSchema();
        ageProp.setTypes(new java.util.LinkedHashSet<>(java.util.Arrays.asList("integer")));

        JsonSchema activeProp = new JsonSchema();
        activeProp.setTypes(new java.util.LinkedHashSet<>(java.util.Arrays.asList("boolean")));

        schema.addProperty("name", nameProp);
        schema.addProperty("age", ageProp);
        schema.addProperty("active", activeProp);
        mt.setSchema(schema);
        content.addMediaType("application/json", mt);

        Pair<String, String> result = ContentParser.getExampleFromContent(content);
        assertEquals("application/json", result.getFirst());
        assertNotNull(result.getSecond());
        assertFalse("Should not be empty", result.getSecond().isEmpty());
        assertNotEquals("null", result.getSecond().trim());
        assertNotEquals("{}", result.getSecond().trim());

        String json = result.getSecond();
        assertTrue("Should contain 'name'", json.contains("name"));
        assertTrue("Should contain 'age'", json.contains("age"));
        assertTrue("Should contain 'active'", json.contains("active"));
        System.out.println("[3.1 Object] " + json);
    }

    @Test
    public void testOpenApi31_StringSchema() {
        Content content = new Content();
        MediaType mt = new MediaType();

        JsonSchema schema = new JsonSchema();
        schema.setTypes(new java.util.LinkedHashSet<>(java.util.Arrays.asList("string")));
        mt.setSchema(schema);
        content.addMediaType("application/json", mt);

        Pair<String, String> result = ContentParser.getExampleFromContent(content);
        assertNotNull(result.getSecond());
        assertFalse(result.getSecond().isEmpty());
        assertNotEquals("null", result.getSecond().trim());
        System.out.println("[3.1 String] " + result.getSecond());
    }

    @Test
    public void testOpenApi31_ArrayWithItems() {
        Content content = new Content();
        MediaType mt = new MediaType();

        JsonSchema schema = new JsonSchema();
        schema.setTypes(new java.util.LinkedHashSet<>(java.util.Arrays.asList("array")));
        JsonSchema items = new JsonSchema();
        items.setTypes(new java.util.LinkedHashSet<>(java.util.Arrays.asList("string")));
        schema.setItems(items);
        mt.setSchema(schema);
        content.addMediaType("application/json", mt);

        Pair<String, String> result = ContentParser.getExampleFromContent(content);
        assertNotNull(result.getSecond());
        assertFalse(result.getSecond().isEmpty());
        assertTrue("Should contain array bracket", result.getSecond().contains("["));
        System.out.println("[3.1 Array] " + result.getSecond());
    }

    @Test
    public void testOpenApi31_NullableField_AnyOf() {
        // FastAPI pattern: anyOf: [{type: "string"}, {type: "null"}]
        Content content = new Content();
        MediaType mt = new MediaType();

        JsonSchema schema = new JsonSchema();
        schema.setTypes(new java.util.LinkedHashSet<>(java.util.Arrays.asList("object")));

        JsonSchema fieldSchema = new JsonSchema();
        JsonSchema stringOption = new JsonSchema();
        stringOption.setTypes(new java.util.LinkedHashSet<>(java.util.Arrays.asList("string")));
        JsonSchema nullOption = new JsonSchema();
        nullOption.setTypes(new java.util.LinkedHashSet<>(java.util.Arrays.asList("null")));
        fieldSchema.setAnyOf(java.util.Arrays.asList(stringOption, nullOption));

        schema.addProperty("optional_field", fieldSchema);
        mt.setSchema(schema);
        content.addMediaType("application/json", mt);

        Pair<String, String> result = ContentParser.getExampleFromContent(content);
        assertNotNull(result.getSecond());
        assertFalse(result.getSecond().isEmpty());
        assertTrue("Should contain optional_field", result.getSecond().contains("optional_field"));
        System.out.println("[3.1 Nullable anyOf] " + result.getSecond());
    }

    @Test
    public void testOpenApi31_EmptySchema() {
        Content content = new Content();
        MediaType mt = new MediaType();
        JsonSchema schema = new JsonSchema();
        mt.setSchema(schema);
        content.addMediaType("application/json", mt);

        Pair<String, String> result = ContentParser.getExampleFromContent(content);
        assertNotNull(result.getSecond());
        assertNotEquals("null", result.getSecond().trim());
        System.out.println("[3.1 Empty] '" + result.getSecond() + "'");
    }

    @Test
    public void testOpenApi31_NestedObjects() {
        Content content = new Content();
        MediaType mt = new MediaType();

        JsonSchema schema = new JsonSchema();
        schema.setTypes(new java.util.LinkedHashSet<>(java.util.Arrays.asList("object")));

        JsonSchema inner = new JsonSchema();
        inner.setTypes(new java.util.LinkedHashSet<>(java.util.Arrays.asList("object")));
        JsonSchema innerProp = new JsonSchema();
        innerProp.setTypes(new java.util.LinkedHashSet<>(java.util.Arrays.asList("string")));
        inner.addProperty("value", innerProp);

        schema.addProperty("nested", inner);
        mt.setSchema(schema);
        content.addMediaType("application/json", mt);

        Pair<String, String> result = ContentParser.getExampleFromContent(content);
        assertNotNull(result.getSecond());
        assertFalse(result.getSecond().isEmpty());
        assertTrue("Should contain nested", result.getSecond().contains("nested"));
        assertTrue("Should contain value", result.getSecond().contains("value"));
        System.out.println("[3.1 Nested] " + result.getSecond());
    }

    @Test
    public void testOpenApi31_FormatAware() {
        Content content = new Content();
        MediaType mt = new MediaType();

        JsonSchema schema = new JsonSchema();
        schema.setTypes(new java.util.LinkedHashSet<>(java.util.Arrays.asList("object")));

        JsonSchema emailProp = new JsonSchema();
        emailProp.setTypes(new java.util.LinkedHashSet<>(java.util.Arrays.asList("string")));
        emailProp.setFormat("email");

        JsonSchema uuidProp = new JsonSchema();
        uuidProp.setTypes(new java.util.LinkedHashSet<>(java.util.Arrays.asList("string")));
        uuidProp.setFormat("uuid");

        JsonSchema dateProp = new JsonSchema();
        dateProp.setTypes(new java.util.LinkedHashSet<>(java.util.Arrays.asList("string")));
        dateProp.setFormat("date-time");

        schema.addProperty("email", emailProp);
        schema.addProperty("id", uuidProp);
        schema.addProperty("created_at", dateProp);
        mt.setSchema(schema);
        content.addMediaType("application/json", mt);

        Pair<String, String> result = ContentParser.getExampleFromContent(content);
        String json = result.getSecond();
        assertNotNull(json);
        assertFalse(json.isEmpty());
        System.out.println("[3.1 Formats] " + json);
    }

    // ========================
    // Swagger 2.0 test
    // ========================

    @Test
    public void testSwagger20_ParsedAsOpenApi30() {
        String spec = "{\n" +
            "  \"swagger\": \"2.0\",\n" +
            "  \"info\": {\"title\": \"Test\", \"version\": \"1.0\"},\n" +
            "  \"host\": \"localhost\",\n" +
            "  \"basePath\": \"/api\",\n" +
            "  \"paths\": {\n" +
            "    \"/users\": {\n" +
            "      \"post\": {\n" +
            "        \"consumes\": [\"application/json\"],\n" +
            "        \"produces\": [\"application/json\"],\n" +
            "        \"parameters\": [{\n" +
            "          \"in\": \"body\",\n" +
            "          \"name\": \"body\",\n" +
            "          \"schema\": {\n" +
            "            \"type\": \"object\",\n" +
            "            \"properties\": {\n" +
            "              \"username\": {\"type\": \"string\"},\n" +
            "              \"age\": {\"type\": \"integer\"}\n" +
            "            }\n" +
            "          }\n" +
            "        }],\n" +
            "        \"responses\": {\n" +
            "          \"200\": {\n" +
            "            \"description\": \"ok\",\n" +
            "            \"schema\": {\n" +
            "              \"type\": \"object\",\n" +
            "              \"properties\": {\n" +
            "                \"id\": {\"type\": \"integer\"},\n" +
            "                \"success\": {\"type\": \"boolean\"}\n" +
            "              }\n" +
            "            }\n" +
            "          }\n" +
            "        }\n" +
            "      }\n" +
            "    }\n" +
            "  }\n" +
            "}";

        ParseOptions options = new ParseOptions();
        options.setResolve(true);
        SwaggerParseResult parseResult = new OpenAPIParser().readContents(spec, null, options);
        OpenAPI openAPI = parseResult.getOpenAPI();
        assertNotNull("Swagger 2.0 should parse to OpenAPI", openAPI);

        // After parsing, Swagger 2.0 is converted to OpenAPI 3.0 with typed subclasses
        Schema<?> reqSchema = openAPI.getPaths().get("/users").getPost()
            .getRequestBody().getContent().get("application/json").getSchema();
        assertFalse("Swagger 2.0 should NOT produce JsonSchema", reqSchema instanceof JsonSchema);
        System.out.println("[Swagger 2.0] schema class=" + reqSchema.getClass().getSimpleName());

        Content reqContent = openAPI.getPaths().get("/users").getPost().getRequestBody().getContent();
        Pair<String, String> reqResult = ContentParser.getExampleFromContent(reqContent);
        assertFalse("Swagger 2.0 request should not be empty", reqResult.getSecond().isEmpty());
        assertTrue(reqResult.getSecond().contains("username"));
        assertTrue(reqResult.getSecond().contains("age"));
        System.out.println("[Swagger 2.0 Req] " + reqResult.getSecond());
    }

    // ========================
    // Full spec parsing tests
    // ========================

    @Test
    public void testFullSpec_OpenApi30() {
        String spec = "{\n" +
            "  \"openapi\": \"3.0.1\",\n" +
            "  \"info\": {\"title\": \"Test\", \"version\": \"1.0\"},\n" +
            "  \"paths\": {\n" +
            "    \"/users\": {\n" +
            "      \"post\": {\n" +
            "        \"requestBody\": {\n" +
            "          \"content\": {\n" +
            "            \"application/json\": {\n" +
            "              \"schema\": {\n" +
            "                \"type\": \"object\",\n" +
            "                \"properties\": {\n" +
            "                  \"username\": {\"type\": \"string\"},\n" +
            "                  \"email\": {\"type\": \"string\", \"format\": \"email\"},\n" +
            "                  \"age\": {\"type\": \"integer\"}\n" +
            "                }\n" +
            "              }\n" +
            "            }\n" +
            "          }\n" +
            "        },\n" +
            "        \"responses\": {\n" +
            "          \"200\": {\n" +
            "            \"description\": \"ok\",\n" +
            "            \"content\": {\n" +
            "              \"application/json\": {\n" +
            "                \"schema\": {\n" +
            "                  \"type\": \"object\",\n" +
            "                  \"properties\": {\n" +
            "                    \"id\": {\"type\": \"integer\"},\n" +
            "                    \"success\": {\"type\": \"boolean\"}\n" +
            "                  }\n" +
            "                }\n" +
            "              }\n" +
            "            }\n" +
            "          }\n" +
            "        }\n" +
            "      }\n" +
            "    }\n" +
            "  }\n" +
            "}";

        ParseOptions options = new ParseOptions();
        options.setResolve(true);
        SwaggerParseResult parseResult = new OpenAPIParser().readContents(spec, null, options);
        OpenAPI openAPI = parseResult.getOpenAPI();
        assertNotNull(openAPI);

        Content reqContent = openAPI.getPaths().get("/users").getPost().getRequestBody().getContent();
        Pair<String, String> reqResult = ContentParser.getExampleFromContent(reqContent);
        assertFalse("3.0 request should not be empty", reqResult.getSecond().isEmpty());
        assertTrue(reqResult.getSecond().contains("username"));
        assertTrue(reqResult.getSecond().contains("email"));
        assertTrue(reqResult.getSecond().contains("age"));

        Content respContent = openAPI.getPaths().get("/users").getPost().getResponses().get("200").getContent();
        Pair<String, String> respResult = ContentParser.getExampleFromContent(respContent);
        assertFalse("3.0 response should not be empty", respResult.getSecond().isEmpty());
        assertTrue(respResult.getSecond().contains("id"));
        assertTrue(respResult.getSecond().contains("success"));

        System.out.println("[Full 3.0 Req] " + reqResult.getSecond());
        System.out.println("[Full 3.0 Resp] " + respResult.getSecond());
    }

    @Test
    public void testFullSpec_OpenApi31() {
        String spec = "{\n" +
            "  \"openapi\": \"3.1.0\",\n" +
            "  \"info\": {\"title\": \"Test\", \"version\": \"1.0\"},\n" +
            "  \"paths\": {\n" +
            "    \"/memory/save\": {\n" +
            "      \"post\": {\n" +
            "        \"requestBody\": {\n" +
            "          \"content\": {\n" +
            "            \"application/json\": {\n" +
            "              \"schema\": {\n" +
            "                \"$ref\": \"#/components/schemas/SaveRequest\"\n" +
            "              }\n" +
            "            }\n" +
            "          }\n" +
            "        },\n" +
            "        \"responses\": {\n" +
            "          \"200\": {\n" +
            "            \"description\": \"ok\",\n" +
            "            \"content\": {\n" +
            "              \"application/json\": {\n" +
            "                \"schema\": {\n" +
            "                  \"type\": \"object\",\n" +
            "                  \"properties\": {\n" +
            "                    \"success\": {\"type\": \"boolean\"},\n" +
            "                    \"id\": {\"type\": \"string\"}\n" +
            "                  }\n" +
            "                }\n" +
            "              }\n" +
            "            }\n" +
            "          }\n" +
            "        }\n" +
            "      }\n" +
            "    }\n" +
            "  },\n" +
            "  \"components\": {\n" +
            "    \"schemas\": {\n" +
            "      \"SaveRequest\": {\n" +
            "        \"type\": \"object\",\n" +
            "        \"properties\": {\n" +
            "          \"tenant_id\": {\"type\": \"string\"},\n" +
            "          \"scope\": {\"type\": \"string\"},\n" +
            "          \"content\": {\"type\": \"string\"},\n" +
            "          \"confidence\": {\"type\": \"number\"},\n" +
            "          \"tags\": {\n" +
            "            \"type\": \"array\",\n" +
            "            \"items\": {\"type\": \"string\"}\n" +
            "          },\n" +
            "          \"nullable_field\": {\n" +
            "            \"anyOf\": [{\"type\": \"string\"}, {\"type\": \"null\"}]\n" +
            "          }\n" +
            "        }\n" +
            "      }\n" +
            "    }\n" +
            "  }\n" +
            "}";

        ParseOptions options = new ParseOptions();
        options.setResolve(true);
        SwaggerParseResult parseResult = new OpenAPIParser().readContents(spec, null, options);
        OpenAPI openAPI = parseResult.getOpenAPI();
        assertNotNull(openAPI);

        // Resolve $refs fully — same as Parser.java does
        new io.swagger.v3.parser.util.ResolverFully().resolveFully(openAPI);

        Schema<?> parsedSchema = openAPI.getPaths().get("/memory/save").getPost()
            .getRequestBody().getContent().get("application/json").getSchema();
        System.out.println("[Debug 3.1] class=" + parsedSchema.getClass().getSimpleName()
            + " type=" + parsedSchema.getType()
            + " types=" + parsedSchema.getTypes()
            + " props=" + parsedSchema.getProperties()
            + " $ref=" + parsedSchema.get$ref()
            + " isJsonSchema=" + (parsedSchema instanceof JsonSchema));

        Content reqContent = openAPI.getPaths().get("/memory/save").getPost().getRequestBody().getContent();
        Pair<String, String> reqResult = ContentParser.getExampleFromContent(reqContent);
        String reqJson = reqResult.getSecond();

        System.out.println("[Full 3.1 Req] '" + reqJson + "'");

        assertFalse("3.1 request should not be empty", reqJson.isEmpty());
        assertNotEquals("null", reqJson.trim());
        assertNotEquals("{}", reqJson.trim());
        assertTrue("Should contain tenant_id", reqJson.contains("tenant_id"));
        assertTrue("Should contain scope", reqJson.contains("scope"));
        assertTrue("Should contain content", reqJson.contains("content"));
        assertTrue("Should contain confidence", reqJson.contains("confidence"));
        assertTrue("Should contain tags", reqJson.contains("tags"));

        Content respContent = openAPI.getPaths().get("/memory/save").getPost().getResponses().get("200").getContent();
        Pair<String, String> respResult = ContentParser.getExampleFromContent(respContent);
        String respJson = respResult.getSecond();

        System.out.println("[Full 3.1 Resp] " + respJson);

        assertFalse("3.1 response should not be empty", respJson.isEmpty());
        assertTrue("Should contain success", respJson.contains("success"));
        assertTrue("Should contain id", respJson.contains("id"));
    }

    @Test
    public void testFullSpec_OpenApi31_WithRefs() {
        // Test $ref resolution + nested objects
        String spec = "{\n" +
            "  \"openapi\": \"3.1.0\",\n" +
            "  \"info\": {\"title\": \"Test\", \"version\": \"1.0\"},\n" +
            "  \"paths\": {\n" +
            "    \"/create\": {\n" +
            "      \"post\": {\n" +
            "        \"requestBody\": {\n" +
            "          \"content\": {\n" +
            "            \"application/json\": {\n" +
            "              \"schema\": {\"$ref\": \"#/components/schemas/Outer\"}\n" +
            "            }\n" +
            "          }\n" +
            "        },\n" +
            "        \"responses\": {\"200\": {\"description\": \"ok\"}}\n" +
            "      }\n" +
            "    }\n" +
            "  },\n" +
            "  \"components\": {\n" +
            "    \"schemas\": {\n" +
            "      \"Inner\": {\n" +
            "        \"type\": \"object\",\n" +
            "        \"properties\": {\n" +
            "          \"value\": {\"type\": \"string\"},\n" +
            "          \"count\": {\"type\": \"integer\"}\n" +
            "        }\n" +
            "      },\n" +
            "      \"Outer\": {\n" +
            "        \"type\": \"object\",\n" +
            "        \"properties\": {\n" +
            "          \"name\": {\"type\": \"string\"},\n" +
            "          \"nested\": {\"$ref\": \"#/components/schemas/Inner\"},\n" +
            "          \"items\": {\n" +
            "            \"type\": \"array\",\n" +
            "            \"items\": {\"$ref\": \"#/components/schemas/Inner\"}\n" +
            "          }\n" +
            "        }\n" +
            "      }\n" +
            "    }\n" +
            "  }\n" +
            "}";

        ParseOptions options = new ParseOptions();
        options.setResolve(true);
        SwaggerParseResult parseResult = new OpenAPIParser().readContents(spec, null, options);
        OpenAPI openAPI = parseResult.getOpenAPI();
        assertNotNull(openAPI);

        // Resolve refs fully like Parser.java does
        new io.swagger.v3.parser.util.ResolverFully().resolveFully(openAPI);

        Content reqContent = openAPI.getPaths().get("/create").getPost().getRequestBody().getContent();
        Pair<String, String> reqResult = ContentParser.getExampleFromContent(reqContent);
        String reqJson = reqResult.getSecond();

        System.out.println("[Full 3.1 Refs] " + reqJson);

        assertFalse("3.1 ref request should not be empty", reqJson.isEmpty());
        assertNotEquals("null", reqJson.trim());
        assertTrue("Should contain name", reqJson.contains("name"));
        assertTrue("Should contain nested", reqJson.contains("nested"));
        assertTrue("Should contain value", reqJson.contains("value"));
        assertTrue("Should contain count", reqJson.contains("count"));
        assertTrue("Should contain items", reqJson.contains("items"));
    }
}
