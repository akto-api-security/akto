package com.akto.wiz;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mongodb.BasicDBObject;

import java.util.List;

public class WizSpecProcessor {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Extracts the raw OpenAPI spec string from the Wiz API "specification" field,
     * which can arrive as a list of objects with a "string" key, or as a plain string.
     */
    public static String extractSpecString(Object specField) {
        if (specField instanceof List) {
            List<?> specList = (List<?>) specField;
            if (!specList.isEmpty() && specList.get(0) instanceof BasicDBObject) {
                return ((BasicDBObject) specList.get(0)).getString("string");
            }
        } else if (specField instanceof String) {
            return (String) specField;
        }
        return null;
    }

    /**
     * Resolves a single Wiz endpoint node into a ready-to-merge JsonNode spec.
     *
     * - If the endpoint carries a spec string, parses and normalises it.
     * - If no spec string is present but path + method are available, builds a
     *   minimal synthetic OpenAPI 3.0 spec so the endpoint is still imported.
     * - Ensures the servers[0].url always has an http/https scheme (defaults to https).
     *
     * Returns null when there is neither a parseable spec nor enough data to synthesise one.
     */
    public static JsonNode resolveSpec(Object specField, String host, String path, String method) throws Exception {
        String specString = extractSpecString(specField);
        JsonNode spec;

        if (specString == null || specString.isEmpty()) {
            if (host == null || host.isEmpty() || path == null || path.isEmpty() || method == null || method.isEmpty()) {
                return null;
            }
            spec = buildSyntheticSpec(host, path, method);
        } else {
            spec = objectMapper.readTree(specString);
        }

        return normaliseServerUrl(spec, host);
    }

    /**
     * Merges a list of per-endpoint JsonNode specs into a single OpenAPI 3.0 document
     * suitable for passing to the Akto parser. Each input spec is expected to carry
     * exactly one path with one method (as produced by resolveSpec).
     */
    public static ObjectNode buildMergedSpec(List<JsonNode> specs) {
        ObjectNode merged = objectMapper.createObjectNode();
        merged.put("openapi", "3.0.1");
        merged.putObject("info").put("title", "Merged API").put("version", "1.0.0");

        ObjectNode mergedPaths = merged.putObject("paths");
        ObjectNode securitySchemes = merged.putObject("components").putObject("securitySchemes");
        ObjectNode mergedSchemas = ((ObjectNode) merged.get("components")).putObject("schemas");

        for (JsonNode spec : specs) {
            String specHost = spec.path("servers").path(0).path("url").asText("");
            if (!spec.path("paths").fieldNames().hasNext()) continue;

            String specPath = spec.path("paths").fieldNames().next();
            JsonNode pathItem = spec.path("paths").path(specPath);
            if (!pathItem.fieldNames().hasNext()) continue;
            String specMethod = pathItem.fieldNames().next();

            if (!mergedPaths.has(specPath)) mergedPaths.putObject(specPath);
            ObjectNode op = ((ObjectNode) mergedPaths.get(specPath)).putObject(specMethod);
            op.putArray("servers").addObject().put("url", specHost);

            JsonNode params = pathItem.path(specMethod).path("parameters");
            if (params.isArray() && params.size() > 0) op.set("parameters", params);

            JsonNode requestBody = pathItem.path(specMethod).path("requestBody");
            if (!requestBody.isMissingNode()) op.set("requestBody", requestBody);

            JsonNode responses = pathItem.path(specMethod).path("responses");
            if (!responses.isMissingNode()) {
                op.set("responses", responses);
            } else {
                op.putObject("responses").putObject("200").put("description", "OK");
            }

            JsonNode security = pathItem.path(specMethod).path("security");
            if (!security.isMissingNode()) op.set("security", security);

            spec.path("components").path("securitySchemes").fields()
                .forEachRemaining(e -> { if (!securitySchemes.has(e.getKey())) securitySchemes.set(e.getKey(), e.getValue()); });
            spec.path("components").path("schemas").fields()
                .forEachRemaining(e -> { if (!mergedSchemas.has(e.getKey())) mergedSchemas.set(e.getKey(), e.getValue()); });
        }

        return merged;
    }

    private static ObjectNode buildSyntheticSpec(String host, String path, String method) {
        String normalisedPath = path.startsWith("/") ? path : "/" + path;
        ObjectNode spec = objectMapper.createObjectNode();
        spec.put("openapi", "3.0.1");
        spec.putObject("info").put("title", "API").put("version", "1.0.0");
        spec.putArray("servers").addObject().put("url", "https://" + host);
        spec.putObject("paths")
            .putObject(normalisedPath)
            .putObject(method.toLowerCase())
            .putObject("responses")
            .putObject("200").put("description", "OK");
        return spec;
    }

    /**
     * Replaces the servers[0].url host in an OpenAPI spec JsonNode with a new host.
     * Used to remap prod/dev gateway hosts to their QA equivalents before import.
     */
    public static JsonNode replaceSpecHost(JsonNode spec, String newHost) {
        if (!(spec instanceof ObjectNode)) return spec;
        ArrayNode servers = (ArrayNode) ((ObjectNode) spec).withArray("servers");
        servers.removeAll().addObject().put("url", "https://" + newHost);
        return spec;
    }

    private static JsonNode normaliseServerUrl(JsonNode spec, String host) {
        if (!(spec instanceof ObjectNode)) return spec;

        String serverUrl = spec.path("servers").path(0).path("url").asText("");
        boolean missingScheme = !serverUrl.startsWith("http://") && !serverUrl.startsWith("https://");
        if (!missingScheme) return spec;

        String baseUrl = serverUrl.isEmpty() ? host : serverUrl;
        if (baseUrl == null || baseUrl.isEmpty()) return spec;

        ArrayNode servers = (ArrayNode) ((ObjectNode) spec).withArray("servers");
        servers.removeAll().addObject().put("url", "https://" + baseUrl);
        return spec;
    }
}
