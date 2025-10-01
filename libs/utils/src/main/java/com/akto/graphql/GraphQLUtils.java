package com.akto.graphql;

import com.akto.dto.HttpResponseParams;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import graphql.language.*;
import graphql.parser.Parser;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;
import graphql.util.TreeTransformerUtil;
import graphql.validation.DocumentVisitor;
import graphql.validation.LanguageTraversal;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;

import java.util.*;

public class GraphQLUtils {//Singleton class
    Parser parser = new Parser();
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final Gson gson = new Gson();
    LanguageTraversal traversal = new LanguageTraversal();
    public static final String __ARGS = "__args";
    public static final String QUERY = "query";

    private static final GraphQLUtils utils = new GraphQLUtils();

    private static final Set<String> allowedPath = new HashSet<>();

    static {
        allowedPath.add("graphql");
        allowedPath.add("query");
    }

    private GraphQLUtils() {
    }

    public static GraphQLUtils getUtils() {
        return utils;
    }

    /**
     * Validates if the parsed JSON object has valid GraphQL structure
     * Checks for: "query" field, valid GraphQL syntax (query/mutation keywords),
     * and either "operationName" or "variables" fields
     */
    private boolean isValidGraphQLPayload(Map jsonObject, String path) {
        if (jsonObject == null) {
            return false;
        }

        // If path contains "graphql", trust it's GraphQL without additional validation
        if (path != null && path.contains("graphql")) {
            return jsonObject.containsKey(QUERY);
        }

        // For paths like "/query", do strict validation to avoid false positives
        Object queryObj = jsonObject.get(QUERY);
        if (queryObj == null || !(queryObj instanceof String)) {
            return false;
        }

        String queryString = (String) queryObj;
        // Check if it contains GraphQL operation keywords
        String trimmedQuery = queryString.trim().toLowerCase();
        boolean hasGraphQLKeyword = trimmedQuery.startsWith("query") ||
                                   trimmedQuery.startsWith("mutation") ||
                                   trimmedQuery.startsWith("subscription") ||
                                   trimmedQuery.startsWith("{"); // Anonymous query

        if (!hasGraphQLKeyword) {
            return false;
        }

        // Additional check: Should have both operationName and variables fields (typical GraphQL structure)
        boolean hasOperationName = jsonObject.containsKey("operationName");
        boolean hasVariables = jsonObject.containsKey("variables");

        return hasOperationName && hasVariables;
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
            return responseParamsList;
        }

        //Start process for graphql parsing
        Map mapOfRequestPayload = null;
        Object[] listOfRequestPayload = null;
        try {
            JSONObject jsonObject = JSON.parseObject(requestPayload);
            Object obj = (Object)jsonObject;
            if (obj instanceof Map) {
                mapOfRequestPayload = (Map) obj;
            } else if (obj instanceof Object[]) {
                listOfRequestPayload = (Object[]) obj;
            } else {
                return responseParamsList;
            }
        } catch (Exception e) {
            //Eat the exception
            return responseParamsList;
        }

        // Validate GraphQL structure after parsing (single parse, no duplication)
        if (listOfRequestPayload != null) {
            // For array payloads, validate first element
            if (listOfRequestPayload.length > 0 && listOfRequestPayload[0] instanceof Map) {
                if (!isValidGraphQLPayload((Map) listOfRequestPayload[0], path)) {
                    return responseParamsList;
                }
            }
        } else if (mapOfRequestPayload != null) {
            if (!isValidGraphQLPayload(mapOfRequestPayload, path)) {
                return responseParamsList;
            }
        } else {
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

    public String deleteGraphqlField(String payload, String field) {
        return editGraphqlField(payload, field, "", "DELETE", false);
    }

    public String addGraphqlField(String payload, String field, String value) {
        return editGraphqlField(payload, field, value, "ADD", false);
    }

    public String addUniqueGraphqlField(String payload, String field, String value) {
        return editGraphqlField(payload, field, value, "ADD", true);
    }

    public String modifyGraphqlField(String payload, String field, String value) {
        return editGraphqlField(payload, field, value, "MODIFY", false);
    }

    /*
     * Used to edit arguments which are not variables.
     */
    public String modifyGraphqlStaticArguments(String payload, String value) {
        return editGraphqlField(payload, "", value, "MODIFY_ARG", false);
    }

    private String editGraphqlField(String payload, String field, String value, String type, boolean unique) {
        String tempVariable = "__tempDummyVariableToReplace";
        Object payloadObj = JSON.parse(payload);
        Object[] payloadList;
        if (payloadObj instanceof Object[]) {
            payloadList = (Object[]) payloadObj;
        } else {
            payloadList = new Object[]{payloadObj};
        }
        for (Object operationObj : payloadList) {
            Map<String, Object> operation = (Map) operationObj;
            String query = (String) operation.get("query");
            if (query == null) {
                continue;
            }
            Node result = new AstTransformer().transform(parser.parseDocument(query), new NodeVisitorStub() {

                @Override
                public TraversalControl visitInlineFragment(InlineFragment node, TraverserContext<Node> context) {
                    switch (type) {
                        case "ADD":
                            boolean found = false;
                            if (unique) {
                                List<Selection> selectionList = node.getSelectionSet().getSelections();
                                for (Selection selection : selectionList) {
                                    if (selection instanceof Field) {
                                        if (value.equals(((Field) selection).getName())) {
                                            found = true;
                                        }
                                    }
                                }
                            }
                            if (!found) {
                                if (field.equals(node.getTypeCondition().getName())) {
                                    Field field1 = Field.newField(tempVariable).build();
                                    if (node.getSelectionSet() != null) {
                                        SelectionSet newSelectionSet = node.getSelectionSet().transform((builder -> {
                                            builder.selection(field1);
                                        }));
                                        Node newNode = node.transform((builder -> {
                                            builder.selectionSet(newSelectionSet);
                                        }));
                                        return TreeTransformerUtil.changeNode(context, newNode);
                                    } else {
                                        return super.visitInlineFragment(node, context);
                                    }
                                }
                            }
                    }
                    return super.visitInlineFragment(node, context);
                }

                @Override
                public TraversalControl visitField(Field node, TraverserContext<Node> context) {
                    String nodeName = node.getName();
                    String alias = node.getAlias();
                    if (nodeName != null && (nodeName.equalsIgnoreCase(field) || field.equals(alias))) {
                        switch (type) {
                            case "MODIFY":
                            case "DELETE":
                                return TreeTransformerUtil.changeNode(context, parser.parseValue(tempVariable));
                            case "ADD":
                                boolean found = false;
                                if (unique) {
                                    List<Selection> selectionList = node.getSelectionSet().getSelections();
                                    for (Selection selection : selectionList) {
                                        if (selection instanceof Field) {
                                            if (value.equals(((Field) selection).getName())) {
                                                found = true;
                                            }
                                        }
                                    }
                                }
                                if (!found) {
                                    Field field1 = Field.newField(tempVariable).build();
                                    if (node.getSelectionSet() != null) {
                                        SelectionSet newSelectionSet = node.getSelectionSet().transform((builder -> {
                                            builder.selection(field1);
                                        }));
                                        Node newNode = node.transform((builder -> {
                                            builder.selectionSet(newSelectionSet);
                                        }));
                                        return TreeTransformerUtil.changeNode(context, newNode);
                                    } else {
                                        return super.visitField(node, context);
                                    }
                                }
                                return super.visitField(node, context);
                            default:
                                return super.visitField(node, context);
                        }
                    } else {
                        return super.visitField(node, context);
                    }
                }

                @Override
                public TraversalControl visitArgument(Argument node, TraverserContext<Node> context) {
                    switch (type) {
                        case "MODIFY_ARG":
                            if (!(node.getValue() instanceof VariableReference)) {
                                Argument arg = new Argument(node.getName(), parser.parseValue(tempVariable));
                                return TreeTransformerUtil.changeNode(context, arg);
                            }
                        default:
                            return super.visitArgument(node, context);
                    }
                }

            });

            String modifiedQuery = AstPrinter.printAst(result);
            if (modifiedQuery.contains(tempVariable)) {
                modifiedQuery = modifiedQuery.replace(tempVariable, value);
                operation.replace("query", modifiedQuery);
            }
        }
        if (payloadObj instanceof Object[]) {
            return gson.toJson(payloadList);
        } else {
            return gson.toJson(payloadList[0]);
        }
    }


    private void updateResponseParamList(HttpResponseParams responseParams, List<HttpResponseParams> responseParamsList, String path, Map mapOfRequestPayload) {
        List<OperationDefinition> operationDefinitions = parseGraphQLRequest(mapOfRequestPayload);

        if (!operationDefinitions.isEmpty()) {
            for (OperationDefinition definition : operationDefinitions) {
                OperationDefinition.Operation operation = definition.getOperation();
                SelectionSet selectionSets = definition.getSelectionSet();
                List<Selection> selectionList = selectionSets.getSelections();
                for (Selection selection : selectionList) {
                    if (selection instanceof Field) {
                        Field field = (Field) selection;
                        String defName = definition.getName() == null ? "" : ("/" + definition.getName());

                        String graphqlPath = path.split("\\?")[0] + "/" + operation.name().toLowerCase() + defName + "/" + field.getName();

                        if (path.contains("?")) {
                            graphqlPath += ("?" + path.split("\\?")[1]);
                        }

                        HttpResponseParams httpResponseParamsCopy = responseParams.copy();
                        httpResponseParamsCopy.requestParams.setUrl(graphqlPath);
                        try {
                            Map<String, Object> map = fieldTraversal(field);
                            HashMap hashMap = new HashMap(mapOfRequestPayload);
                            for (String key : map.keySet()) {
                                hashMap.put(GraphQLUtils.QUERY + key, map.get(key));
                            }
                            hashMap.remove(GraphQLUtils.QUERY);
                            httpResponseParamsCopy.requestParams.setPayload(JSON.toJSONString(hashMap));
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
