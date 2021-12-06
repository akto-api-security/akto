package com.akto.oas;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.akto.oas.requests.BxB;
import com.akto.oas.requests.RequestTree;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;

public class RequestsGenerator {

    private static boolean isTemplated(String path) {
        return path.contains("{");
    }

    private static Map<String, Map<String, Parameter>> getInXNameToParams(List<Parameter> parameters) {
        Map<String, Map<String, Parameter>> ret = new HashMap<>();

        Map<String, Parameter> headerMap = new HashMap<>();
        Map<String, Parameter> cookieMap = new HashMap<>();
        Map<String, Parameter> queryMap = new HashMap<>();
        Map<String, Parameter> pathMap = new HashMap<>();

        ret.put("path", pathMap);
        ret.put("header", headerMap);
        ret.put("cookie", cookieMap);
        ret.put("query", queryMap);

        for(Parameter parameter: parameters) {
            Map<String, Parameter> assignedMap;
            switch(parameter.getIn().toLowerCase()) {
                case "path":
                    assignedMap = pathMap;
                    break;
                case "header":
                    assignedMap = headerMap;
                    break;
                case "query":
                    assignedMap = queryMap;
                    break;
                case "cookie":
                    assignedMap = cookieMap;
                    break;
                default:  
                    throw new IllegalStateException("Invalid parameter in: " + parameter.getIn());
            }

            assignedMap.put(parameter.getName(), parameter);
        }

        return ret;
    }

    public static Map<String, List<BxB>> createRequests(OpenAPI openAPI) {

        Map<String, List<BxB>> ret = new HashMap<>();

        String baseURL = openAPI.getServers().get(0).getUrl();

        for(String pathStr: openAPI.getPaths().keySet()) {
            Map<String, List<BxB>> requestsMap = createRequests(baseURL + pathStr, openAPI.getPaths().get(pathStr));
            
            List<BxB> happyTotal = ret.getOrDefault("happy", new ArrayList<>());
            List<BxB> allTotal = ret.getOrDefault("all", new ArrayList<>());

            happyTotal.addAll(requestsMap.get("happy"));
            allTotal.addAll(requestsMap.get("all"));

            ret.put("happy", happyTotal);
            ret.put("all", allTotal);
        }

        return ret;
    }

    private static RequestTree createForAll(Operation operation, String pathStr, String method) {
        try { 
            List<BxB> pathFunctors = new ArrayList<>();
            pathFunctors.addAll(getFuncsForPath(operation, pathStr, method));

            List<BxB> headerFunctors = new ArrayList<>();
            headerFunctors.addAll(getFuncsForHeaders(operation, pathStr, method));

            List<BxB> queryFunctors = new ArrayList<>();
            queryFunctors.addAll(getFuncsForQuery(operation, pathStr, method));

            List<BxB> cookieFunctors = new ArrayList<>();
            cookieFunctors.add(getFuncForCookie(operation, pathStr, method));

            return new RequestTree(pathFunctors, null).wrapWith(queryFunctors).wrapWith(headerFunctors).wrapWith(cookieFunctors);
        } catch (Exception e) {
            System.out.println(e.getMessage());
            return null;
        }
    }

    private static Map<String, List<BxB>> createRequests(String pathStr, PathItem pathItem) {
        Map<String, List<BxB>> ret = new HashMap<>();

        List<BxB> happy = new ArrayList<>();
        List<BxB> all = new ArrayList<>();

        ret.put("happy", happy);
        ret.put("all", all);

        {
            Operation getOperation = pathItem.getGet();
            if (getOperation != null) {
                RequestTree getTree = createForAll(getOperation, pathStr, "GET");
                List<BxB> list = new RequestTree(BxB.createGet().toList(), getTree).composeDeepReverse();

                happy.add(list.get(0));
                all.addAll(list);
            }
        }
        
        {
            Operation deleteOperation = pathItem.getDelete();
            if (deleteOperation != null) {
                RequestTree deleteTree = createForAll(deleteOperation, pathStr, "DELETE");
                List<BxB> list = new RequestTree(BxB.createDelete().toList(), deleteTree).composeDeepReverse();
                happy.add(list.get(0));
                all.addAll(list);
            }
        }
        
        {
            Operation postOperation = pathItem.getPost();
            if (postOperation != null) {
                RequestTree postTree = createForAll(postOperation, pathStr, "POST");
                String[] requestBodies = extractSampleRequestBody(postOperation);

                List<BxB> result;
                if (requestBodies == null) {
                    result = BxB.createErr(BxB.NO_SAMPLE, "POST " + pathStr).toList();
                } else {
                    result = new ArrayList<>();
                    for(String requestBody: requestBodies) {
                        result.add(BxB.createPost(requestBody));
                    }
                }

                List<BxB> list = new RequestTree(result, postTree).composeDeepReverse();
                happy.add(list.get(0));
                all.addAll(list);

            }
        }

        {
            Operation putOperation = pathItem.getPut();
            if (putOperation != null) {
                RequestTree putTree = createForAll(putOperation, pathStr, "PUT");
                String[] requestBodies = extractSampleRequestBody(putOperation);

                List<BxB> result;
                if (requestBodies == null) {
                    result = BxB.createErr(BxB.NO_SAMPLE, "PUT " + pathStr).toList();
                } else {
                    result = new ArrayList<>();
                    for(String requestBody: requestBodies) {
                        result.add(BxB.createPut(requestBody));
                    }
                }

                List<BxB> list = new RequestTree(result, putTree).composeDeepReverse();
                happy.add(list.get(0));
                all.addAll(list);
            }
        }
        
        return ret; 
    }

    private static String[] extractSampleRequestBody(Operation operation) {
        RequestBody requestBody =  operation.getRequestBody();
        String[] ret = new String[]{"{}"};
        if (requestBody == null) 
            return ret;

        Content content = requestBody.getContent();
        if (content == null)
            return ret;

        for (Map.Entry<String, MediaType> entry: content.entrySet()) {
            switch(entry.getKey().toLowerCase()) {
                case "application/json": 
                    String[] sampleBodies = BxB.extractJSON(entry.getValue().getSchema());
                    return sampleBodies;
                default: 
                    return ret;
            }
        }
    
        return ret;
    }

    private static List<BxB> getFuncsForPath(Operation operation, String path, String method) {
        List<Parameter> parameters = operation.getParameters();

        Map<String, Map<String, Parameter>> allParametersMap = parameters != null ? getInXNameToParams(parameters) : new HashMap<>();
        
        Map<String, Parameter> pathParamsMap = allParametersMap.getOrDefault("path", new HashMap<>());

        Map<String, String[]> varNameToReplacements = new HashMap<>();

        String origPath = path;

        while (isTemplated(path)) {
            String varName = path.substring(path.indexOf('{')+1, path.indexOf('}'));
            Parameter pathParam = pathParamsMap.getOrDefault(varName, null);

            if (pathParam == null) {
                return BxB.createErr(BxB.NO_PARAMETER, method + " " + path + "> " + varName).toList();
            }
            
            String[] replacements = BxB.extractStr(pathParam.getSchema());

            if (replacements == null || replacements.length == 0) {
                return BxB.createErr(BxB.NO_SAMPLE, method + " " + path + "> " + varName).toList();
            } else {
                varNameToReplacements.put(varName, replacements);
                path = path.replace("{"+varName+"}", replacements[0]);
            }
        }

        
        List<BxB> ret = new ArrayList<>();
        try {
            URI uri = new URI(path);
            ret.add(BxB.createURIBxB(uri));
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Bad URL path: " + path);
        }

        if (varNameToReplacements.isEmpty())
            return ret;

        for (int i = 0; i < varNameToReplacements.values().iterator().next().length; i ++) {
            String tempPath = origPath;

            while(isTemplated(tempPath)) {
                String varName = tempPath.substring(tempPath.indexOf('{')+1, tempPath.indexOf('}'));
                tempPath = tempPath.replace("{"+varName+"}", varNameToReplacements.get(varName)[i]);
            }
        
            try {
                URI uri = new URI(tempPath);
                ret.add(BxB.createURIBxB(uri));
            } catch (URISyntaxException e) {
                throw new IllegalStateException("Bad URL path: " + tempPath);
            }
        }

        return ret;
    }

    private static BxB getFuncForCookie(Operation operation, String pathStr, String method) {
        List<Parameter> parameters = operation.getParameters();

        if (parameters == null) {
            return BxB.createIdentity();
        }

        Map<String, Map<String, Parameter>> allParametersMap = getInXNameToParams(parameters);
        Map<String, Parameter> cookieParamsMap = allParametersMap.get("cookie");

        return BxB.createCookie(cookieParamsMap, pathStr, method);
    }

    private static List<BxB> getFuncsForHeaders(Operation operation, String context, String method) {
        List<Parameter> parameters = operation.getParameters();

        if (parameters == null) {
            return BxB.createIdentity().toList();
        }

        Map<String, Map<String, Parameter>> allParametersMap = getInXNameToParams(parameters);
        Map<String, Parameter> headerParamsMap = allParametersMap.get("header");

        return BxB.createHeaderFunctors(headerParamsMap, method + " " + context + "> parameters > header");
    }

    private static List<BxB> getFuncsForQuery(Operation operation, String pathStr, String method) {

        List<Parameter> parameters = operation.getParameters();

        if (parameters == null) {
            return BxB.createIdentity().toList();
        }

        Map<String, Map<String, Parameter>> allParametersMap = getInXNameToParams(parameters);        
        Map<String, Parameter> queryParamsMap = allParametersMap.get("query");

        return BxB.createQueryFunctors(queryParamsMap, method + " " + pathStr);
    }

}