package com.akto.graphql;

import graphql.language.*;
import graphql.parser.Parser;
import graphql.validation.DocumentVisitor;
import graphql.validation.LanguageTraversal;
import org.mortbay.util.ajax.JSON;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class GraphQLUtils {//Singleton class
    Parser parser = new Parser();
    LanguageTraversal traversal = new LanguageTraversal();
    public static final String __ARGS = "__args";

    private static final GraphQLUtils utils = new GraphQLUtils();
    private GraphQLUtils(){}

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
                        System.out.println(currPath);
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
                                map.put(currPath + "." + __ARGS + "." + argument.getName(),argument.getValue().toString());
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

    public List<OperationDefinition> parseGraphQLRequest(String requestPayload) {
        List<OperationDefinition> result = new ArrayList<>();
        Object obj  = JSON.parse(requestPayload);
        if (obj instanceof HashMap) {
            HashMap map = (HashMap) obj;
            String query = (String) map.get("query");
            try {
                Document document = parser.parseDocument(query);
                List<Definition> definitionList = document.getDefinitions();
                for (Definition definition : definitionList) {
                    if (definition instanceof OperationDefinition) {
                        result.add((OperationDefinition) definition);
                    }
                }
            } catch (Exception e) {
                //eat exception
                return result;
            }
        }
        return result;
    }

}
