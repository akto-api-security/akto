package com.akto.graphql;

import com.akto.dto.HttpResponseParams;
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

        //Start process for graphql parsing
        Map mapOfRequestPayload = null;
        Object[] listOfRequestPayload = null;
        try {
            Object obj = JSON.parse(requestPayload);
            if (obj instanceof Map) {
                mapOfRequestPayload = (Map) obj;
            } else if (obj instanceof Object[]){
                listOfRequestPayload = (Object[]) obj;
            } else {
                return responseParamsList;
            }
        } catch (Exception e) {
            //Eat the exception
            return responseParamsList;
        }

        if (listOfRequestPayload != null) {
            for (Object obj : listOfRequestPayload) {
                if (obj instanceof Map) {
                    updateResponseParamList(responseParams, responseParamsList, path, (Map) obj);
                }
            }
        } else {
            updateResponseParamList(responseParams, responseParamsList, path, mapOfRequestPayload);
        }
        return responseParamsList;
    }

    private void updateResponseParamList(HttpResponseParams responseParams, List<HttpResponseParams> responseParamsList, String path, Map mapOfRequestPayload) {
        List<OperationDefinition> operationDefinitions = parseGraphQLRequest(mapOfRequestPayload);

        if (!operationDefinitions.isEmpty())  {
            for (OperationDefinition definition : operationDefinitions) {
                OperationDefinition.Operation operation = definition.getOperation();
                SelectionSet selectionSets = definition.getSelectionSet();
                List<Selection> selectionList = selectionSets.getSelections();
                for (Selection selection : selectionList) {
                    if (selection instanceof Field) {
                        Field field = (Field) selection;
                        String graphqlPath = path + "/" + operation.name().toLowerCase() + "/" + field.getName();
                        HttpResponseParams httpResponseParamsCopy = responseParams.copy();
                        httpResponseParamsCopy.requestParams.setUrl(graphqlPath);
                        try {
                            Map<String, Object> map = fieldTraversal(field);
                            HashMap hashMap = new HashMap(mapOfRequestPayload);
                                for (String key : map.keySet()) {
                                    hashMap.put(GraphQLUtils.QUERY + key, map.get(key));
                                }
                                hashMap.remove(GraphQLUtils.QUERY);
                                httpResponseParamsCopy.requestParams.setPayload(JSON.toString(hashMap));
                                responseParamsList.add(httpResponseParamsCopy);
                        } catch (Exception e) {
                            //eat exception, No changes to request payload, parse Exception
                        }
                    }
                }
            }
        }
    }

    public List<OperationDefinition> parseGraphQLRequest(Map requestPayload) {
        List<OperationDefinition> result = new ArrayList<>();
        try {
            String query = (String) requestPayload.get(QUERY);
            Document document = parser.parseDocument(query);
            List<Definition> definitionList = document.getDefinitions();
            for (Definition definition : definitionList) {
                if (definition instanceof OperationDefinition) {
                    result.add((OperationDefinition) definition);
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
