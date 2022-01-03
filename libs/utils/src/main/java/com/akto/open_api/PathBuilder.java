package com.akto.open_api;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;

public class PathBuilder {

    public static void addPathItem(Paths paths, String url, String method , int responseCode, Schema<?> schema) throws Exception {
        PathItem pathItem = paths.getOrDefault(url, new PathItem());
        Operation operation = getOperation(pathItem,method);
        if (operation == null) operation = new Operation();

        ApiResponses apiResponses = operation.getResponses();
        if (apiResponses == null) apiResponses = new ApiResponses();

        ApiResponse apiResponse = new ApiResponse();
        Content content = new Content();
        MediaType mediaType = new MediaType();
        mediaType.setSchema(schema);
        content.put("application/json", mediaType);
        apiResponse.setContent(content);
        apiResponses.put(responseCode+"", apiResponse);

        operation.setResponses(apiResponses);
        pathItem.setPost(operation);
        paths.addPathItem(url, pathItem);
    }

    public static Operation getOperation(PathItem pathItem, String method) throws Exception {
        switch (method.toLowerCase()) {
            case "get":
                return pathItem.getGet();
            case "post":
                return pathItem.getPost();
            case "delete":
                return pathItem.getDelete();
            case "put":
                return pathItem.getPut();
            case "patch":
                return pathItem.getPatch();
            case "head":
                return pathItem.getHead();
            case "options":
                return pathItem.getOptions();
            case "trace":
                return pathItem.getTrace();
            default:
                throw new Exception("invalid operation");
        }
    }
}
