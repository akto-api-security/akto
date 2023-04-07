package com.akto.graphql;

import com.akto.dto.HttpResponseParams;
import com.google.api.client.json.Json;
import com.mongodb.BasicDBObject;

import graphql.language.*;
import graphql.parser.Parser;
import graphql.validation.DocumentVisitor;
import graphql.validation.LanguageTraversal;
import org.mortbay.util.ajax.JSON;

import java.util.*;

public class GraphQLUtils {//Singleton class
    Parser parser = new Parser();
    LanguageTraversal traversal = new LanguageTraversal();
    public static final String __ARGS = "__args";
    public static final String QUERY = "query";
    public static final String OPERATION_NAME = "operationName";

    private static final GraphQLUtils utils = new GraphQLUtils();

    private static final Set<String> allowedPath = new HashSet<>();

    static {
        allowedPath.add("graphql");
    }

    private GraphQLUtils() {
    }

    public static GraphQLUtils getUtils() {
        return utils;
    }

    public HashMap<String, Object> fieldTraversal(Field field) {
        HashMap<String, Object> map = new HashMap<>();

        traversal.traverse(field, new DocumentVisitor() {
            String currPath = "";

            @Override
            public void enter(Node node, List<Node> path) {
                if (node instanceof Field) {
                    currPath += ("." + ((Field) node).getName());
                    if (node.getChildren() == null || node.getChildren().isEmpty()) {//Last Node
                        map.put(currPath, true);
                    }
                    List<Argument> arguments = ((Field) node).getArguments();
                    if (arguments != null && arguments.size() != 0) {
                        for (Argument argument : arguments) {
                            Value value = argument.getValue();
                            if (value instanceof StringValue) {
                                map.put(currPath + "." + __ARGS + "." + argument.getName(), ((StringValue) argument.getValue()).getValue());
                            } else if (value instanceof IntValue) {
                                map.put(currPath + "." + __ARGS + "." + argument.getName(), ((IntValue) argument.getValue()).getValue());
                            } else if (value instanceof FloatValue) {
                                map.put(currPath + "." + __ARGS + "." + argument.getName(), ((FloatValue) argument.getValue()).getValue());
                            } else if (value instanceof BooleanValue) {
                                map.put(currPath + "." + __ARGS + "." + argument.getName(), ((BooleanValue) argument.getValue()).isValue());
                            } else {
                                map.put(currPath + "." + __ARGS + "." + argument.getName(), argument.getValue().toString());
                            }
                        }
                    }

                }
            }

            @Override
            public void leave(Node node, List<Node> path) {
                if (currPath.isEmpty()) return;
                if (node instanceof Field) {
                    currPath = currPath.substring(0, currPath.lastIndexOf("."));
                }
            }
        });
        return map;
    }

    private static class JsonToMapOrList {
        Map<String, Object> map;
        Object[] list;

        private JsonToMapOrList(){}

        static JsonToMapOrList create(String payload) {
            Object obj = JSON.parse(payload);
            JsonToMapOrList ret = new JsonToMapOrList();
            if (obj instanceof Map) {
                ret.map = (Map) obj;
                return ret;
            } else if (obj instanceof Object[]){
                ret.list = (Object[]) obj;
                return ret;
            }

            return null;
        }
        
    }

    public List<HttpResponseParams> parseGraphqlResponseParam(HttpResponseParams responseParams) {
        List<HttpResponseParams> responseParamsList = new ArrayList<>();
        String path = responseParams.getRequestParams().getURL();

        boolean isAllowedForParse = false;

        for (String graphqlPath : allowedPath) {
            if (path != null && path.contains(graphqlPath)) {
                isAllowedForParse = true;
            }
        }
        String requestPayload = responseParams.getRequestParams().getPayload();
        if (!isAllowedForParse || !requestPayload.contains(QUERY)) {
            // DO NOT PARSE as it's not graphql query
            return responseParamsList;
        }

        JsonToMapOrList reqMapOrList;

        try {
            reqMapOrList = JsonToMapOrList.create(requestPayload);
            if (reqMapOrList == null) {
                return responseParamsList;
            }
        } catch (Exception e) {
            return responseParamsList;
        }

        JsonToMapOrList respMapOrList = JsonToMapOrList.create(responseParams.getPayload());

        if (reqMapOrList.list != null) {
            boolean matchByIndex = respMapOrList != null && respMapOrList.list != null && respMapOrList.list.length == reqMapOrList.list.length;
            int index = 0;
            
            for (Object obj : reqMapOrList.list) {
                index ++;
                if (obj instanceof Map) {

                    HttpResponseParams responseParamsCopy = responseParams.copy();
                    if (matchByIndex) {
                        responseParamsCopy.setPayload(respMapOrList.list[index].toString());
                    }

                    updateResponseParamList(responseParamsCopy, responseParamsList, path, (Map) obj);
                }
            }
        } else {
            updateResponseParamList(responseParams, responseParamsList, path, reqMapOrList.map);
        }
        return responseParamsList;
    }

    private void updateResponseParamList(HttpResponseParams responseParams, List<HttpResponseParams> responseParamsList, String path, Map mapOfRequestPayload) {
        List<OperationDefinition> operationDefinitions = parseGraphQLRequest(mapOfRequestPayload);
        HashMap<String, Object> hashMap = new HashMap<>(mapOfRequestPayload);
        String origResponse = responseParams.getPayload();
        Object origResponseObject = JSON.parse(origResponse);

        if (!operationDefinitions.isEmpty())  {
            System.out.println("Found " + operationDefinitions.size() + " operations");
            for (OperationDefinition definition : operationDefinitions) {
                OperationDefinition.Operation operation = definition.getOperation();
                String operationName = mapOfRequestPayload.containsKey(OPERATION_NAME) ? mapOfRequestPayload.get(OPERATION_NAME).toString() : null;

                String defName =  definition.getName() == null ? "" : ( "/" + definition.getName());
                System.out.println("Opening up OperationDefnName: " + defName +" OperationName: " + operation.name());
                SelectionSet selectionSets = definition.getSelectionSet();
                List<Selection> selectionList = selectionSets.getSelections();
                System.out.println("Found " + selectionList.size() + " selections");

                for (Selection selection : selectionList) {
                    if (selection instanceof Field) {
                        Field field = (Field) selection;
                        System.out.println("Found fieldName: " + field.getName());
                        try {
                            Map<String, Object> map = fieldTraversal(field);
                            for (String key : map.keySet()) {
                                hashMap.put(GraphQLUtils.QUERY + "." + field.getName() + "." + key, map.get(key));
                            }
                        } catch (Exception e) {
                            //eat exception, No changes to request payload, parse Exception
                        }
                    }
                }

                String graphqlPath = path.split("\\?")[0] + "/" + operation.name().toLowerCase() + defName;

                if (path.contains("?")) {
                    graphqlPath += ("?"+path.split("\\?")[1]);
                }

                HttpResponseParams httpResponseParamsCopy = responseParams.copy();
                httpResponseParamsCopy.requestParams.setUrl(graphqlPath);
                httpResponseParamsCopy.requestParams.setPayload(JSON.toString(hashMap));
                httpResponseParamsCopy.setPayload();
                responseParamsList.add(httpResponseParamsCopy);
                hashMap.remove(GraphQLUtils.QUERY);
            }
        }
    }

    public List<OperationDefinition> parseGraphQLRequest(Map requestPayload) {
        List<OperationDefinition> result = new ArrayList<>();
        try {
            String query = (String) requestPayload.get(QUERY);

            String operationName = requestPayload.containsKey(OPERATION_NAME) ? requestPayload.get(OPERATION_NAME).toString() : null;

            Document document = parser.parseDocument(query);
            List<Definition> definitionList = document.getDefinitions();
            for (Definition definition : definitionList) {
                if (definition instanceof OperationDefinition) {
                    OperationDefinition operationDefinition = (OperationDefinition) definition;
                    if (operationName == null || operationName.equalsIgnoreCase(operationDefinition.getName())) {
                        result.add(operationDefinition);
                        
                        if (operationName != null) break;
                    }
                }
            }
        } catch (Exception e) {
            result.clear();
            //eat exception
            return result;
        }
        return result;
    }

}
