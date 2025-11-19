package com.akto.threat.detection.tasks;

import com.akto.dto.HttpResponseParams;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
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
import java.util.Iterator;

@Getter
@Setter
@AllArgsConstructor
public class RequestValidator {
  private static List<SchemaConformanceError> errors = new ArrayList<>();
  private static SchemaConformanceError.Builder errorBuilder = SchemaConformanceError.newBuilder();

  private static ObjectMapper objectMapper = new ObjectMapper();
  private static final LoggerMaker logger = new LoggerMaker(MaliciousTrafficDetectorTask.class, LogDb.THREAT_DETECTION);
  private static JsonSchemaFactory factory = JsonSchemaFactory.getInstance(VersionFlag.V202012,
      builder -> builder.metaSchema(OpenApi31.getInstance())
          .defaultMetaSchemaIri(OpenApi31.getInstance().getIri()));

  public static List<SchemaConformanceError> getErrors() {
    return errors;
  }

  public static String transformTrafficUrlToSchemaUrl(JsonNode rootSchemaNode, String url) {

    if (!rootSchemaNode.path("servers").isArray()) {
      return url;
    }

    // Remove the server URL prefix from the input URL
    for (JsonNode server : rootSchemaNode.path("servers")) {

      if (server.path("url").isMissingNode()) {
        continue;
      }

      String serverUrl = server.path("url").asText();

      if (url.startsWith(serverUrl)) {
        url = url.substring(serverUrl.length());
        break;
      }

    }

    JsonNode pathsNode = rootSchemaNode.path("paths");

    if (pathsNode.isMissingNode() || !pathsNode.isObject()) {
      logger.debug("No paths found in schema for api collection id {}, path {}, method {}",
          rootSchemaNode.path("info").path("title").asText(),
          url,
          "GET");
      return url;
    }

    Iterator<String> pathKeys = pathsNode.fieldNames();

    while (pathKeys.hasNext()) {
      String pathKey = pathKeys.next();
      if (matchesParameterizedPath(pathKey, url)) {
        return pathKey;
      }
    }

    return url;
  }

  private static boolean matchesParameterizedPath(String pathKey, String url) {

    // TODO: handle cases like
    // /pets/{petId} and /pets/mine both are present in schema

    String[] pathSegments = pathKey.split("/");
    String[] urlSegments = url.split("/");

    if (pathSegments.length != urlSegments.length) {
      return false;
    }

    for (int i = 0; i < pathSegments.length; i++) {
      String pathSegment = pathSegments[i];
      String urlSegment = urlSegments[i];

      // Skip if the path segment is a parameter (e.g., {userId})
      if (pathSegment.startsWith("{") && pathSegment.endsWith("}")) {
        continue;
      }

      if (!pathSegment.equals(urlSegment)) {
        return false;
      }
    }

    return true;
  }

  public static JsonNode getRequestBodySchemaNode(JsonNode rootSchemaNode, HttpResponseParams responseParam) {
    String url = transformTrafficUrlToSchemaUrl(rootSchemaNode, responseParam.getRequestParams().getURL());

    JsonNode pathNode = getPathNode(rootSchemaNode, url, responseParam);
    if (pathNode == null) {
      return null;
    }

    JsonNode methodNode = getMethodNode(pathNode, url, responseParam);
    if (methodNode == null) {
      return null;
    }

    // Skip request body validation for GET/DELETE requests
    String method = responseParam.getRequestParams().getMethod().toLowerCase();
    if(method.equals("get") || method.equals("delete")){
      return null;
    }

    JsonNode requestBodyNode = getRequestBodyNode(methodNode, url, responseParam);
    if (requestBodyNode == null) {
      return null;
    }

    return getContentSchemaNode(requestBodyNode, responseParam);
  }

  private static JsonNode getPathNode(JsonNode rootSchemaNode, String url, HttpResponseParams responseParam) {
    JsonNode pathNode = rootSchemaNode.path("paths").path(url);

    if (pathNode.isMissingNode()) {
      addError("#/paths", url, "url", "URL path not found in schema: " + responseParam.getRequestParams().getURL());
      return null;
    }

    return pathNode;
  }

  private static JsonNode getMethodNode(JsonNode pathNode, String url, HttpResponseParams responseParam) {
    String method = responseParam.getRequestParams().getMethod().toLowerCase();
    JsonNode methodNode = pathNode.path(method);

    if (methodNode.isMissingNode()) {
      addError("#/paths" + url, method, "method",
          String.format("Method %s not available for path %s in schema", method.toUpperCase(),
              responseParam.getRequestParams().getURL()));
      return null;
    }

    return methodNode;
  }

  private static JsonNode getRequestBodyNode(JsonNode methodNode, String url, HttpResponseParams responseParam) {
    JsonNode requestBodyNode = methodNode.path("requestBody");

    if (requestBodyNode.isMissingNode()) {
      String method = responseParam.getRequestParams().getMethod().toLowerCase();
      addError("#/paths" + url + "/" + method, url + "." + method + ".requestBody", "requestBody",
          String.format("Request body not available for method %s for path %s in schema",
              responseParam.getRequestParams().getMethod(), responseParam.getRequestParams().getURL()));
      return null;
    }

    return requestBodyNode;
  }

  private static JsonNode getContentSchemaNode(JsonNode requestBodyNode, HttpResponseParams responseParam) {
    String contentType = responseParam.getRequestParams().getHeaders().get("content-type").get(0);

    if(!contentType.equalsIgnoreCase("application/json")) {
      // validate only json content type
      return null;
    }

    JsonNode schemaNode = requestBodyNode.path("content").path(contentType).path("schema");

    if (schemaNode.isMissingNode()) {
      String method = responseParam.getRequestParams().getMethod().toLowerCase();
      addError(
          "#/paths" + responseParam.getRequestParams().getURL() + "/" + method + "/requestBody/content/" + contentType,
          "", "requestBody",
          String.format("Schema not available for method %s for path %s for content-type %s in schema",
              responseParam.getRequestParams().getMethod(), responseParam.getRequestParams().getURL(), contentType));
      return null;
    }
    return schemaNode;
  }

  private static void addError(String schemaPath, String instancePath, String attribute, String message) {
    errors.clear();
    errorBuilder.clear();
    errorBuilder.setSchemaPath(schemaPath);
    errorBuilder.setInstancePath(instancePath);
    errorBuilder.setAttribute(attribute);
    errorBuilder.setMessage(message);
    errorBuilder.setLocation(SchemaConformanceError.Location.LOCATION_BODY);
    errorBuilder.setStart(-1);
    errorBuilder.setEnd(-1);
    errors.add(errorBuilder.build());
  }

  private static List<SchemaConformanceError> validateRequestBody(HttpResponseParams httpResponseParams,
      JsonNode rootSchemaNode) throws Exception {

    JsonNode schemaNode = getRequestBodySchemaNode(rootSchemaNode, httpResponseParams);

    if (!errors.isEmpty()) {
      return errors;
    }

    if (schemaNode == null || schemaNode.isMissingNode()) {
      logger.warn("No request body schema found for api collection id {}, path {}, method {}",
          httpResponseParams.getRequestParams().getApiCollectionId(),
          httpResponseParams.getRequestParams().getURL(),
          httpResponseParams.getRequestParams().getMethod());

      // TODO: Case of shadow API vs body not found due to incorrect schema/parsing?
      // For shadow API we should mark it as a threat ??
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

    errors.clear();
    for (ValidationMessage message : validationMessages) {
      errorBuilder.clear();
      errorBuilder.setSchemaPath(message.getSchemaLocation().toString());
      errorBuilder.setInstancePath(message.getInstanceLocation().toString());
      errorBuilder.setAttribute("requestBody");
      errorBuilder.setMessage(message.getMessage());
      errorBuilder.setLocation(SchemaConformanceError.Location.LOCATION_BODY);
      errorBuilder.setStart(-1);
      errorBuilder.setEnd(-1);
      errorBuilder.setPhrase(message.getMessage());
      errors.add(errorBuilder.build());
    }

    return errors;
  }

  public static List<SchemaConformanceError> validate(HttpResponseParams responseParam, String apiSchema,
      String apiInfoKey) {
    try {

      // Reset the errors, done to prevent new obj allocations..
      errors.clear();
      errorBuilder.clear();

      JsonNode rootSchemaNode = objectMapper.readTree(apiSchema);

      if (rootSchemaNode == null || rootSchemaNode.isEmpty()) {
        logger.errorAndAddToDb(String.format("Unable to parse schema for api info key: %s", apiInfoKey));
        return errors;
      }

      return validateRequestBody(responseParam, rootSchemaNode);
    } catch (Exception e) {
      logger.errorAndAddToDb(e, "Error conforming to schema for api info key" + apiInfoKey);
      return errors;
    }
  }

}
