package com.akto.open_api;

import com.akto.dto.type.APICatalog;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PathBuilder {

    public static void addPathItem(Paths paths, String url, String method , int responseCode, Schema<?> schema,List<Parameter> headerParameters, List<Parameter> queryParameters, boolean includeHeaders) throws Exception {
        PathItem pathItem = paths.getOrDefault(url, new PathItem());
        pathItem.setDescription("description");
        Operation operation = getOperation(pathItem,method);
        if (operation == null) {
            operation = new Operation();
            operation.setOperationId(generateOperationId(url, method));
            operation.setSummary(generateSummary(url, method));
        }

        if (responseCode == -1) {
            // Only add requestBody for methods that support it (POST, PUT, PATCH, etc.)
            // GET, DELETE, HEAD, OPTIONS, TRACE cannot have a request body per HTTP spec
            String methodLower = method.toLowerCase();
            boolean supportsRequestBody = methodLower.equals("post") || 
                                         methodLower.equals("put") || 
                                         methodLower.equals("patch");
            
            if (supportsRequestBody) {
                RequestBody requestBody = new RequestBody();
                Content requestBodyContent = requestBody.getContent();
                if (requestBodyContent == null) requestBodyContent = new Content();

                MediaType mediaType = new MediaType();
                mediaType.setSchema(schema);
                requestBodyContent.put("application/json", mediaType);

                requestBody.setContent(requestBodyContent);
                operation.setRequestBody(requestBody);
            }
            
            List<Parameter> parameters = new ArrayList<>();
            if (includeHeaders) {
                parameters.addAll(headerParameters);
            }
            parameters.addAll(queryParameters);
            operation.setParameters(parameters);
            setOperation(pathItem, method, operation);
            paths.addPathItem(url, pathItem);
            return ;
        }

        ApiResponses apiResponses = operation.getResponses();
        if (apiResponses == null) apiResponses = new ApiResponses();

        ApiResponse apiResponse = new ApiResponse();
        Content content = new Content();
        MediaType mediaType = new MediaType();
        mediaType.setSchema(schema);
        content.put("application/json", mediaType);
        apiResponse.setContent(content);
        apiResponse.setDescription("description");
        if (includeHeaders) {
            Map<String,Header> headers = paramListToHeader(headerParameters);
            apiResponse.setHeaders(headers);
        }
        apiResponses.put(responseCode+"", apiResponse);

        operation.setResponses(apiResponses);
        setOperation(pathItem, method, operation);
        paths.addPathItem(url, pathItem);
    }

    public static Map<String,Header> paramListToHeader(List<Parameter> headerParameters){
        Map<String,Header> headers = new HashMap<>();
        for(Parameter parameter: headerParameters){
            if(!headers.containsKey(parameter.getName())){
                Header head = new Header();
                head.setSchema(parameter.getSchema());
                headers.put(parameter.getName(),head);
            }
        }
        return headers;
    }

    public static Paths parameterizePath(Paths paths) {
        Paths newPaths = new Paths();
        for (String url: paths.keySet()) {
            PathItem pathItem = paths.get(url);
            List<Parameter> parameters = new ArrayList<>();
            String[] urlList = url.split("/");
            int idx = 0;
            for (int i=0;i< urlList.length; i++) {
                String u = urlList[i];
                if (APICatalog.isTemplateUrl(u)) {
                    idx += 1;
                    String paramName = "{param" + idx + "}";
                    urlList[i] = paramName;
                    Parameter parameter = new Parameter();
                    parameter.setIn("path");
                    parameter.setName("param"+idx);
                    if (u.equals("INTEGER")) {
                        parameter.setSchema(new IntegerSchema());
                    } else {
                        parameter.setSchema(new StringSchema());
                    }
                    parameters.add(parameter);
                }
            }
            String newUrl = String.join( "/",urlList);
            if (!newUrl.startsWith("/")) {
                newUrl = "/" + newUrl;
            }


            pathItem.setParameters(parameters);
            newPaths.put(newUrl, pathItem);
        }
        return newPaths;
    }

    public static String generateOperationId(String url, String method) {
        return url + "-" + method;
    }

    public static String generateSummary(String url, String method) {
        return method + " request for endpoint " + url;
    }

    public static void setOperation(PathItem pathItem, String method, Operation operation) throws Exception {
        switch (method.toLowerCase()) {
            case "get":
                pathItem.setGet(operation);
                return ;
            case "post":
                pathItem.setPost(operation);
                return ;
            case "delete":
                pathItem.setDelete(operation);
                return ;
            case "put":
                pathItem.setPut(operation);
                return ;
            case "patch":
                pathItem.setPatch(operation);
                return ;
            case "head":
                pathItem.setHead(operation);
                return ;
            case "options":
                pathItem.setOptions(operation);
                return ;
            case "trace":
                pathItem.setTrace(operation);
                return ;
            default:
                throw new Exception("invalid operation");
        }
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
