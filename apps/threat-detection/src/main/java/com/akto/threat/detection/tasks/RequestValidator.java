package com.akto.threat.detection.tasks;

import com.akto.dto.HttpResponseParams;
import com.akto.log.LoggerMaker;
import com.akto.proto.generated.threat_detection.message.sample_request.v1.Metadata;
import com.akto.proto.generated.threat_detection.message.sample_request.v1.SchemaConformanceError;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion.VersionFlag;
import com.networknt.schema.ValidationMessage;
import com.networknt.schema.oas.OpenApi31;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Set;
import java.util.ArrayList;

@Getter
@Setter
@AllArgsConstructor
public class RequestValidator {
  private static List<SchemaConformanceError> errors;

  private static ObjectMapper objectMapper = new ObjectMapper();
  private static final LoggerMaker logger = new LoggerMaker(MaliciousTrafficDetectorTask.class);
  private static JsonSchemaFactory factory = JsonSchemaFactory.getInstance(VersionFlag.V202012,
      builder -> builder.metaSchema(OpenApi31.getInstance())
          .defaultMetaSchemaIri(OpenApi31.getInstance().getIri()));

  private static String getUrlToSearch(JsonNode rootSchemaNode, String url) {
    if (!rootSchemaNode.path("servers").isArray()) {
        return url;
    }

    // Remove the server URL prefix from the input URL
    for (JsonNode server : rootSchemaNode.path("servers")) {
        if (server.path("url").isMissingNode()) {
            return url;
        }
        String serverUrl = server.path("url").asText();
        if (url.startsWith(serverUrl)) {
            url = url.substring(serverUrl.length());
            break;
        }
    }

    // Match the URL against the paths in the schema
    JsonNode pathsNode = rootSchemaNode.path("paths");
    if (pathsNode.isMissingNode() || !pathsNode.isObject()) {
        return url; // No paths defined, return the original URL
    }
    // Iterate through the paths and check for a match
    // for (String pathKey : pathsNode.fieldNames()) {
    //     if (isPathMatching(pathKey, url)) {
    //         return pathKey; // Return the matching path key
    //     }
    // }

    return url; 
}

private static boolean isPathMatching(String pathKey, String url) {
    // Split the path and URL into segments
    String[] pathSegments = pathKey.split("/");
    String[] urlSegments = url.split("/");

    if (pathSegments.length != urlSegments.length) {
        return false; // The number of segments must match
    }

    // Compare each segment
    for (int i = 0; i < pathSegments.length; i++) {
        String pathSegment = pathSegments[i];
        String urlSegment = urlSegments[i];

        // If the path segment is a parameter (e.g., {petId}), skip the comparison
        if (pathSegment.startsWith("{") && pathSegment.endsWith("}")) {
            continue;
        }

        // Otherwise, the segments must match exactly
        if (!pathSegment.equals(urlSegment)) {
            return false;
        }
    }

    return true; // All segments match
}

  private static JsonNode getRequestBodySchema(JsonNode schemaNode, HttpResponseParams responseParam) {
    JsonNode paths = schemaNode.path("paths");
    String url = getUrlToSearch(schemaNode, responseParam.getRequestParams().getURL());
    JsonNode requestBodySchemaNode = paths.path(url)
        .path(responseParam.getRequestParams().getMethod().toLowerCase())
        .path("requestBody")
        .path("content")
        .path(responseParam.getRequestParams().getHeaders().get("content-type").get(0))
        .path("schema");

    return requestBodySchemaNode;
  }

  private static List<SchemaConformanceError> validateRequestBody(HttpResponseParams httpResponseParams,
      JsonNode rootSchemaNode) throws Exception {

    JsonNode schemaNode = getRequestBodySchema(rootSchemaNode, httpResponseParams);

    if (schemaNode == null || schemaNode.isMissingNode()) {
      logger.warn("No request body schema found for api collection id {}, path {}, method {}",
          httpResponseParams.getRequestParams().getApiCollectionId(),
          httpResponseParams.getRequestParams().getURL(),
          httpResponseParams.getRequestParams().getMethod());

      return errors;
    }

    JsonNode dataNode = objectMapper.readTree(httpResponseParams.getRequestParams().getPayload());

    JsonSchema schema = factory.getSchema(schemaNode);

    Set<ValidationMessage> validationMessages = schema.validate(dataNode);

    if (validationMessages.isEmpty()) {

      return errors;
    }

    logger.debug("Request not conforming to schema for api collection id {}",
        httpResponseParams.getRequestParams().getApiCollectionId());

    errors = new ArrayList<>();
    for (ValidationMessage message : validationMessages) {
      SchemaConformanceError.Builder errorBuilder = SchemaConformanceError.newBuilder();
      errorBuilder.setSchemaPath(message.getSchemaLocation().toString());
      errorBuilder.setInstancePath(message.getInstanceLocation().toString());
      errorBuilder.setAttribute("requestBody");
      errorBuilder.setMessage(message.getMessage());
      errors.add(errorBuilder.build());
    }

    return errors;
  }

  public static List<SchemaConformanceError> validate(HttpResponseParams responseParam, String apiSchema, String apiInfoKey) {
    try {
      

      ObjectMapper objectMapper = new ObjectMapper();

      JsonNode rootSchemaNode = objectMapper.readTree(apiSchema);
      if (rootSchemaNode == null || rootSchemaNode.isEmpty()) {
        logger.debug("Unable to parse schema for api info key", apiInfoKey);
        return errors;
      }

      return validateRequestBody(responseParam, rootSchemaNode);
    } catch (Exception e) {
      logger.error("Error conforming to schema for api info key  {}",
          apiInfoKey, e);
      return errors;
    }
  }
}
