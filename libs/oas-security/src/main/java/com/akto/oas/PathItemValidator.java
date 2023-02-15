package com.akto.oas;

import com.akto.oas.Issue.Type;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PathItemValidator {
    public static List<Issue> validate(PathItem pathItem, List<io.swagger.v3.oas.models.tags.Tag> definedTags,
                                       String pathName, Map<String, SecurityScheme> securitySchemeMap, List<String> path) {
        List<Issue> issues = new ArrayList<>();

        Map<String,String> parameterMapFromPathName = generateParameterMapFromPathName(pathName);

        path.add(PathComponent.PARAMETERS);
        issues.addAll(validateParameters(pathItem.getParameters(),pathName,true, parameterMapFromPathName, path));
        // after this step parameterMapFromPathName contains only parameterNames which were not defined in parameters
        // we then check if they exist in parameters of operation
        // if not we raise errors
        path.remove(path.size()-1);
        Map<String, String> remainingParameterMap = new HashMap<>();
        for (PathItem.HttpMethod operationType: pathItem.readOperationsMap().keySet()) {
            Operation operation = pathItem.readOperationsMap().get(operationType);
            remainingParameterMap= new HashMap<>(parameterMapFromPathName);
            path.add(operationType.toString().toLowerCase());
            issues.addAll(validateOperation(operationType, operation, definedTags, securitySchemeMap,
                    remainingParameterMap,pathName, path));
            path.remove(path.size()-1);
        }

        issues.addAll(OpenAPIValidator.validateServers(pathItem.getServers(),false, path));


        path.add(PathComponent.REF);
//        result.addResults(validateRef(pathItem.get$ref(), path));
        path.remove(path.size()-1);

        return issues;
    }

    public static Map<String, String> generateParameterMapFromPathName(String pathName) {
        Pattern p = Pattern.compile("\\{(.*?)\\}");
        List<String> separated = Arrays.asList(pathName.split("\\?"));
        String pathNameBeforeQuestionMark = separated.get(0);
        Map<String,String> parameterMapFromPathName = new HashMap<>();
        Matcher m;
        m = p.matcher(pathNameBeforeQuestionMark);
        while(m.find()) {
            parameterMapFromPathName.put(m.group(1), "path");
        }
        if (separated.size() > 1) {
            String pathNameAfterQuestionMark = separated.get(1);
            m = p.matcher(pathNameAfterQuestionMark);
            while(m.find()) {
                parameterMapFromPathName.put(m.group(1), "query");
            }
        }
        return parameterMapFromPathName;
    }

    public static List<Issue> validateParameters(List<Parameter> parameters, String pathName, boolean inPath,
                                                 Map<String, String> parameterMapFromPathName, List<String> path) {
        List<Issue> issues = new ArrayList<>();

        Map<String,String> store = new HashMap<>();
        int index = 0;
        if (parameters == null) parameters = new ArrayList<>();
        for (Parameter parameter: parameters) {
            path.add(index+"");

            if (parameter.getIn() == null) {
                issues.add(Issue.generateParameterInFieldIssue(path));
            } else if (!Arrays.asList("query", "header", "path", "cookie").contains(parameter.getIn())) {
                issues.add(Issue.generateParameterPropertyIssue(path));
            }

            if (parameter.getIn().equals("path") && (!parameter.getRequired())) {
                issues.add(Issue.generatePathParamRequiredIssue(path));
            }

            String name = parameter.getName();
            String key = name + "." + parameter.getIn();
            // check for duplicates parameters
            String val = parameterMapFromPathName.get(name);
            if (store.get(key) != null) {
                issues.add(Issue.generateDuplicateParameterIssue(path));
            } else {
                store.put(key,name);
                // checking if the parameter defined exists in the url or not
                // also check if same "in" i.e. path to path and query to query
                // doing it inside 'else' because we want to raise either duplicate issue or not found issue (since we are removing keys from parameterMapFromPathName)
                // TODO: raise separate issues 1. for null 2. not correct "in"
                if (Objects.equals(val, parameter.getIn())) {
                    parameterMapFromPathName.remove(name);
                } else {
                    issues.add(Issue.generateNoCorrespondingPathTemplateIssue(name, pathName, path));
                }
            }

            path.add(PathComponent.SCHEMA);
            issues.addAll(SchemaValidator.validate(parameter.getSchema(),path));
            path.remove(path.size()-1);

            path.remove(path.size()-1);
            index += 1;
        }

        if (!inPath) {
            for (String p: parameterMapFromPathName.keySet()) {
                String in = parameterMapFromPathName.get(p);
                issues.add(Issue.generateNoCorrespondingPathParamIssue(in, p, path));
            }
        }

        return issues;
    }

    public static List<Issue> validateOperation(PathItem.HttpMethod operationType, Operation operation,
                                                List<io.swagger.v3.oas.models.tags.Tag> definedTags,
                                                Map<String, SecurityScheme> securitySchemeMap,
                                                Map<String, String> remainingParameterMap, String pathName,
                                                List<String> path) {
        List<Issue> issues = new ArrayList<>();
        issues.addAll(validateTagsInPath(operation.getTags(),definedTags, path));

        issues.addAll(OpenAPIValidator.validateServers(operation.getServers(),false, path));

//        validateExternalDocs(operation.getExternalDocs(), path);

        issues.addAll(validateOperationId(operation.getOperationId(), path));

        issues.addAll(validateParameters(operation.getParameters(),pathName,false,remainingParameterMap, path));

        issues.addAll(validateRequestBody(operation.getRequestBody(), path));

        issues.addAll(validateResponses(operation.getResponses(), operationType, path));

//        validateCallbacks(operation.getCallbacks(), path);

        issues.addAll(OpenAPIValidator.validateSecurity(operation.getSecurity(),securitySchemeMap,false, path));


        return issues;
    }

    public static List<Issue> validateOperationId(String operationId, List<String> path) {
        List<Issue> issues = new ArrayList<>();
        if (operationId == null) return issues;
        path.add("operation_id");
        path.remove(path.size()-1);

        return issues;
    }

    public static List<Issue> validateTagsInPath(List<String> tags, List<Tag> definedTags, List<String> path) {
        List<Issue> issues = new ArrayList<>();
        if (tags == null) return issues;
        path.add("tags");
        path.remove(path.size()-1);

        return issues;
    }
    public static  List<Issue> validateContent(Content content, List<String> path) {
        List<Issue> issues = new ArrayList<>();
        if (content == null) return issues;
        path.add("content");

        for (String contentType: content.keySet()) {
            path.add(contentType);
            MediaType mediaType = content.get(contentType);

            path.add("schema");
            if (mediaType.getSchema() != null) {
                issues.addAll(SchemaValidator.validate(mediaType.getSchema(), path));
            }
            path.remove(path.size()-1);

//            result.addResults(validateExamples(mediaType.getExamples(), path));
//            result.addResults(validateExample(mediaType.getExample(), path));
//            result.addResults(validateEncoding(mediaType.getEncoding(), path));
            path.remove(path.size()-1);
        }

        path.remove(path.size()-1);
        return issues;
    }


    public static List<Issue> validateResponses(ApiResponses apiResponses, PathItem.HttpMethod httpMethod, List<String> path) {
        List<Issue> issues = new ArrayList<>();
        if (apiResponses == null) return issues;
        path.add("responses");

        // TODO: securityDefined
        boolean securityDefined = false;
        issues.addAll(ResponseDefinitionValidator.validateResponseDefinitions(httpMethod,apiResponses.keySet(), path, securityDefined));

        for (String responseCode: apiResponses.keySet()) {
            path.add(responseCode);

            ApiResponse apiResponse  = apiResponses.get(responseCode);
//            result.addResults(validateResponseHeaders(apiResponse.getHeaders(),path));
            issues.addAll(validateContent(apiResponse.getContent(),path));
//            result.addResults(validateLinks(apiResponse.getLinks(),path));
//            result.addResults(validateExtensions(apiResponse.getExtensions(),path));
//            result.addResults(validateRef(apiResponse.get$ref(),path));

            path.remove(path.size()-1);
        }

        path.remove(path.size()-1);
        return issues;
    }

    // TODO:
    public static List<Issue> validateResponseHeaders(Map<String, Header> headerMap, List<String> path) {
        List<Issue> issues = new ArrayList<>();
        if (headerMap == null) return issues;
        path.add("headers");
        for (String headerName: headerMap.keySet()) {
            path.add(headerName);
            Header header = headerMap.get(headerName);

            header.getRequired();
            header.getDeprecated();
            header.getStyle();
            header.getExplode();
            header.getSchema();
            header.getExample();
            header.getExamples();
            header.getContent();
            header.get$ref();

            path.remove(path.size()-1);
        }

        path.remove(path.size()-1);
        return issues;
    }

    public static List<Issue> validateRequestBody(RequestBody requestBody, List<String> path) {
        List<Issue> issues = new ArrayList<>();
        if (requestBody == null) return issues;
        path.add("requestBody");
        issues.addAll(validateContent(requestBody.getContent(), path));
        path.remove(path.size()-1);

        return issues;
    }
}

