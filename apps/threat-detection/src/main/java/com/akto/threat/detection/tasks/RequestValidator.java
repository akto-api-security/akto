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

    // TODO handle urls with path params. Eg: /api/v1/pet/{petId}
    for (JsonNode server : rootSchemaNode.path("servers")) {

      // Example {servers: [{url: "https://api.example.com/api/v1"}]}
      // url: /api/v1/pet
      // output: /pet
      if (server.path("url").isMissingNode()) {
        return url;
      }
      String serverUrl = server.path("url").asText();
      if (url.startsWith(serverUrl)) {
        return url.substring(serverUrl.length());
      }
    }

    return url;
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

    if (schemaNode == null || schemaNode.isEmpty()) {
      logger.debug("No request body schema found for api collection id {}",
          httpResponseParams.getRequestParams().getApiCollectionId());

      return errors;
    }

    JsonNode dataNode = objectMapper.readTree(httpResponseParams.getRequestParams().getPayload());
    dataNode = objectMapper.readTree(
        "{\"id\":10,\"name\":\"Dogs\",\"category\":{\"id\":\"test\"},\"tags\":[{\"id\":0,\"name\":\"string\"}],\"status\":\"available\"}");

    // Create a JsonSchema instance
    JsonSchema schema = factory.getSchema(schemaNode);

    // Validate the data against the schema
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

  public static List<SchemaConformanceError> validate(HttpResponseParams responseParam, String apiSchema) {
    try {
      if (apiSchema == null || apiSchema.isEmpty()) {
        return errors;
      }

      ObjectMapper objectMapper = new ObjectMapper();

      JsonNode rootSchemaNode = objectMapper.readTree(apiSchema);
      if (rootSchemaNode == null || rootSchemaNode.isEmpty()) {
        logger.debug("No schema found for api collection id {}", responseParam.getRequestParams().getApiCollectionId());
        return errors;
      } 

      return validateRequestBody(responseParam, rootSchemaNode);
    } catch (Exception e) {
      logger.warn("Request not conforming to schema for api collection id {}",
          responseParam.getRequestParams().getApiCollectionId());
      return errors;
    }
  }
}
