package com.akto.threat.detection.tasks;

import com.akto.data_actor.DataActor;
import com.akto.dto.ApiInfo;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.type.URLMethods;
import com.akto.dto.type.URLTemplate;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.proto.generated.threat_detection.message.sample_request.v1.SchemaConformanceError;
import com.akto.threat.detection.cache.AccountConfig;
import com.akto.threat.detection.cache.AccountConfigurationCache;

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
import java.util.Map;
import java.util.HashMap;
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

    // Strip query string for path matching
    String urlPath = stripQueryString(url);

    // First: check for exact path match (preferred over parameterized)
    if (pathsNode.has(urlPath)) {
      return urlPath;
    }

    // Second: check for parameterized path match
    Iterator<String> pathKeys = pathsNode.fieldNames();

    while (pathKeys.hasNext()) {
      String pathKey = pathKeys.next();
      if (matchesParameterizedPath(pathKey, url)) {
        return pathKey;
      }
    }

    return url;
  }

  private static String stripQueryString(String url) {
    int queryIndex = url.indexOf('?');
    return queryIndex != -1 ? url.substring(0, queryIndex) : url;
  }

  private static boolean matchesParameterizedPath(String pathKey, String url) {

    // Strip query string before matching
    String urlPath = stripQueryString(url);

    String[] pathSegments = pathKey.split("/");
    String[] urlSegments = urlPath.split("/");

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

  public static JsonNode getRequestBodySchemaNode(JsonNode methodNode, HttpResponseParams responseParam,
      String normalizedUrl) {

    // Skip request body validation for GET/DELETE requests
    String method = responseParam.getRequestParams().getMethod().toLowerCase();
    if (method.equals("get") || method.equals("delete")) {
      return null;
    }

    JsonNode requestBodyNode = getRequestBodyNode(methodNode, normalizedUrl, responseParam);
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

    if (!contentType.equalsIgnoreCase("application/json")) {
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

  public static void addError(String schemaPath, String instancePath, String attribute, String message,
      SchemaConformanceError.Location location) {
    errorBuilder.clear();
    errorBuilder.setSchemaPath(schemaPath);
    errorBuilder.setInstancePath(instancePath);
    errorBuilder.setAttribute(attribute);
    errorBuilder.setMessage(message);
    errorBuilder.setLocation(location);
    errorBuilder.setStart(-1);
    errorBuilder.setEnd(-1);
    errors.add(errorBuilder.build());
  }

  public static void addError(String schemaPath, String instancePath, String attribute, String message) {
    addError(schemaPath, instancePath, attribute, message, SchemaConformanceError.Location.LOCATION_BODY);
  }

  /**
   * Returns a map of parameter names to their required status.
   * Key: parameter name (lowercase), Value: true if required, false if optional
   */
  private static Map<String, Boolean> getSchemaDefinedParameters(JsonNode methodNode, JsonNode pathNode, String paramLocation) {
    Map<String, Boolean> definedParams = new HashMap<>();

    // Check parameters at path level first (can be overridden by method level)
    JsonNode pathParams = pathNode.path("parameters");
    if (pathParams.isArray()) {
      for (JsonNode param : pathParams) {
        if (paramLocation.equals(param.path("in").asText())) {
          String paramName = param.path("name").asText().toLowerCase();
          boolean isRequired = param.path("required").asBoolean(false);
          definedParams.put(paramName, isRequired);
        }
      }
    }

    // Check parameters at method level (overrides path level)
    JsonNode methodParams = methodNode.path("parameters");
    if (methodParams.isArray()) {
      for (JsonNode param : methodParams) {
        if (paramLocation.equals(param.path("in").asText())) {
          String paramName = param.path("name").asText().toLowerCase();
          boolean isRequired = param.path("required").asBoolean(false);
          definedParams.put(paramName, isRequired);
        }
      }
    }

    return definedParams;
  }

  private static Map<String, String> parseQueryParamsFromUrl(String url) {
    Map<String, String> queryParams = new java.util.HashMap<>();

    int queryStart = url.indexOf('?');
    if (queryStart == -1) {
      return queryParams;
    }

    String queryString = url.substring(queryStart + 1);
    String[] pairs = queryString.split("&");

    for (String pair : pairs) {
      int eqIndex = pair.indexOf('=');
      if (eqIndex > 0) {
        String key = pair.substring(0, eqIndex).toLowerCase();
        String value = eqIndex < pair.length() - 1 ? pair.substring(eqIndex + 1) : "";
        queryParams.put(key, value);
      } else if (!pair.isEmpty()) {
        queryParams.put(pair.toLowerCase(), "");
      }
    }

    return queryParams;
  }

  private static void validateHeaders(HttpResponseParams httpResponseParams, JsonNode methodNode,
      JsonNode pathNode, String schemaPath) {
    Map<String, Boolean> definedHeaders = getSchemaDefinedParameters(methodNode, pathNode, "header");
    Map<String, List<String>> actualHeaders = httpResponseParams.getRequestParams().getHeaders();

    // Check for missing required headers
    for (Map.Entry<String, Boolean> entry : definedHeaders.entrySet()) {
      if (entry.getValue()) { // if required
        String requiredHeader = entry.getKey();
        boolean found = actualHeaders != null && actualHeaders.keySet().stream()
            .anyMatch(h -> h.toLowerCase().equals(requiredHeader));
        if (!found) {
          addError(schemaPath + "/parameters", requiredHeader, "header",
              String.format("Required header '%s' is missing", requiredHeader),
              SchemaConformanceError.Location.LOCATION_HEADER);
        }
      }
    }
  }

  private static void validateQueryParams(HttpResponseParams httpResponseParams, JsonNode methodNode,
      JsonNode pathNode, String schemaPath) {
    Map<String, Boolean> definedQueryParams = getSchemaDefinedParameters(methodNode, pathNode, "query");
    String url = httpResponseParams.getRequestParams().getURL();
    Map<String, String> actualQueryParams = parseQueryParamsFromUrl(url);

    // Check for missing required query parameters
    for (Map.Entry<String, Boolean> entry : definedQueryParams.entrySet()) {
      if (entry.getValue()) { // if required
        String requiredParam = entry.getKey();
        if (!actualQueryParams.containsKey(requiredParam)) {
          addError(schemaPath + "/parameters", requiredParam, "query",
              String.format("Required query parameter '%s' is missing", requiredParam),
              SchemaConformanceError.Location.LOCATION_URL);
        }
      }
    }
  }

  private static void validateRequestBody(HttpResponseParams httpResponseParams,
      JsonNode methodNode, String normalizedUrl) throws Exception {

    int errorsBefore = errors.size();

    // Reuse existing method to get schema node (handles GET/DELETE skip,
    // content-type check, etc.)
    JsonNode schemaNode = getRequestBodySchemaNode(methodNode, httpResponseParams, normalizedUrl);

    // If there were errors from getRequestBodySchemaNode, they're already added
    if (errors.size() > errorsBefore) {
      return;
    }

    // schemaNode can be null for GET/DELETE or non-JSON content types (not an
    // error)
    if (schemaNode == null || schemaNode.isMissingNode()) {
      return;
    }

    String payload = httpResponseParams.getRequestParams().getPayload();
    if (payload == null || payload.isEmpty()) {
      return;
    }

    JsonNode dataNode = objectMapper.readTree(payload);

    // Validate using JSON Schema library
    JsonSchema schema = factory.getSchema(schemaNode);
    Set<ValidationMessage> validationMessages = schema.validate(dataNode);

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

      String url = transformTrafficUrlToSchemaUrl(rootSchemaNode, responseParam.getRequestParams().getURL());

      // Reuse existing helper methods
      JsonNode pathNode = getPathNode(rootSchemaNode, url, responseParam);
      if (pathNode == null) {
        return errors;
      }

      JsonNode methodNode = getMethodNode(pathNode, url, responseParam);
      if (methodNode == null) {
        return errors;
      }

      String method = responseParam.getRequestParams().getMethod().toLowerCase();
      String schemaPath = "#/paths" + url + "/" + method;

      validateHeaders(responseParam, methodNode, pathNode, schemaPath);
      validateQueryParams(responseParam, methodNode, pathNode, schemaPath);
      validateRequestBody(responseParam, methodNode, url);

      return errors;
    } catch (Exception e) {
      logger.errorAndAddToDb(e, "Error conforming to schema for api info key" + apiInfoKey);
      return errors;
    }
  }

  public static List<SchemaConformanceError> handleSchemaConformFilter(HttpResponseParams responseParam, ApiInfo.ApiInfoKey apiInfoKey, List<SchemaConformanceError> errors, DataActor dataActor) {
    if (responseParam.getStatusCode() < 200 || responseParam.getStatusCode() >= 300) {
      return errors;
    }

    AccountConfig config = AccountConfigurationCache.getInstance().getConfig(dataActor);
    Map<String, Set<URLMethods.Method>> apiInfoUrlToMethods = config.getApiInfoUrlToMethods();
    Map<Integer, List<URLTemplate>> apiCollectionUrlTemplates = config.getApiCollectionUrlTemplates();

    if (apiInfoUrlToMethods.isEmpty() && apiCollectionUrlTemplates.isEmpty()) {
      logger.infoAndAddToDb("No api infos found for validating schema");
      return errors;
    }

    int apiCollectionId = apiInfoKey.getApiCollectionId();
    String url = apiInfoKey.getUrl();
    URLMethods.Method method = apiInfoKey.getMethod();

    url = ApiInfo.getNormalizedUrl(url);

    String urlKey = apiCollectionId + ":" + url;
    Set<URLMethods.Method> methods = apiInfoUrlToMethods.get(urlKey);

    if (methods != null && methods.contains(method)) {
      return errors;
    } else if (methods != null && !methods.contains(method)) {
      addError("#/paths" + url, method.name(), "method",
          String.format("Method %s not available for path %s in discovered traffic",
              method.name(), responseParam.getRequestParams().getURL()));
      return getErrors();
    }

    List<URLTemplate> urlTemplates = apiCollectionUrlTemplates.get(apiCollectionId);
    if (urlTemplates == null || urlTemplates.isEmpty()) {
      logger.debugAndAddToDb("Schema conformance error: URL not found in discovered traffic - " + url + " " + method);
      addError("#/paths", url, "url", "API not found in discovered traffic: " + method + " " + url);
      return getErrors();
    }

    for (URLTemplate urlTemplate : urlTemplates) {
      URLTemplate.MatchResult result = urlTemplate.matchTemplate(url, method);
      if (result == URLTemplate.MatchResult.FULL_MATCH) {
        return errors;
      }
      if (result == URLTemplate.MatchResult.URL_MATCH_METHOD_MISMATCH) {
        addError("#/paths" + url, method.name(), "method",
            String.format("Method %s not available for path %s template %s in discovered traffic",
                method.name(), responseParam.getRequestParams().getURL(), urlTemplate.getTemplateString()));
        return getErrors();
      }
    }

    addError("#/paths", url, "url", "API not found in discovered traffic: " + method + " " + url);
    return getErrors();
  }

}
