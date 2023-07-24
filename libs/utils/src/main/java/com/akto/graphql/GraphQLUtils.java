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
import org.mortbay.util.ajax.JSON;

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

    public String deleteGraphqlField(String payload, String field) {
        Object[] payloadList = (Object []) JSON.parse(payload);
        for (Object operationObj: payloadList) {
            Map<String, Object> operation = (Map) operationObj;
            String query = (String) operation.get("query");
            Node result = new AstTransformer().transform(parser.parseDocument(query), new NodeVisitorStub() {

                @Override
                public TraversalControl visitField(Field node, TraverserContext<Node> context) {
                    if (node.getName().equals(field)) {
                        return TreeTransformerUtil.deleteNode(context);
                    } else {
                        return super.visitField(node, context);
                    }
                }
            });
            String modifiedQuery = AstPrinter.printAst(result);
            operation.replace("query", modifiedQuery);
        }
        return gson.toJson(payloadList);
    }

    public String addGraphqlField(String payload, String field, String value) {
        String tempVariable = "__tempDummyVariableToReplace";
        Object[] payloadList = (Object []) JSON.parse(payload);
        for (Object operationObj: payloadList) {
            Map<String, Object> operation = (Map) operationObj;
            String query = (String) operation.get("query");
            Node result = new AstTransformer().transform(parser.parseDocument(query), new NodeVisitorStub() {

                @Override
                public TraversalControl visitField(Field node, TraverserContext<Node> context) {
                    if (node.getName().equals(field)) {
                        return TreeTransformerUtil.insertAfter(context, parser.parseValue(tempVariable));
                    } else {
                        return super.visitField(node, context);
                    }
                }
            });
            String modifiedQuery = AstPrinter.printAst(result);

            modifiedQuery = modifiedQuery.replace(tempVariable, value);
            operation.replace("query", modifiedQuery);
        }
        return gson.toJson(payloadList);
    }

    public String modifyGraphqlField(String payload, String field, String value) {
        String tempVariable = "__tempDummyVariableToReplace";
        Object[] payloadList = (Object []) JSON.parse(payload);
        for (Object operationObj: payloadList) {
            Map<String, Object> operation = (Map) operationObj;
            String query = (String) operation.get("query");
            Node result = new AstTransformer().transform(parser.parseDocument(query), new NodeVisitorStub() {

                @Override
                public TraversalControl visitField(Field node, TraverserContext<Node> context) {
                    if (node.getName().equals(field)) {
                        return TreeTransformerUtil.changeNode(context, parser.parseValue(tempVariable));
                    } else {
                        return super.visitField(node, context);
                    }
                }
            });
            String modifiedQuery = AstPrinter.printAst(result);
            modifiedQuery = modifiedQuery.replace(tempVariable, value);
            operation.replace("query", modifiedQuery);
        }
        return gson.toJson(payloadList);
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
                        String defName =  definition.getName() == null ? "" : ( "/" + definition.getName());

                        String graphqlPath = path.split("\\?")[0] + "/" + operation.name().toLowerCase() + defName + "/"+ field.getName();

                        if (path.contains("?")) {
                            graphqlPath += ("?"+path.split("\\?")[1]);
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



    public static void main(String[] args) {
        String gql1 = "mutation { login(input: {username: \"user\", password: \"12345\"}) {status}}";
        String gql2 = "mutation UpdateLastWatchTime($videoId: ID!, $timestamp: Int) {\n  updateLastWatchTime(videoId: $videoId, timestamp: $timestamp) {\n    ... on UpdateWatchTimePayload {\n      success\n      __typename\n    }\n    __typename\n  }\n}\n";
        String gql3 = "query GetWorkspaceSettings($names: [String!]) {\n  settings: getWorkspaceSettings(names: $names) {\n    ... on WorkspaceSettings {\n      memberInvitationAllowed\n      __typename\n    }\n    __typename\n  }\n}\n";
        String gql4 = "query FetchChapters($videoId: ID!) {\n fetchVideoChapters(videoId: $videoId) {\n ... on VideoChapters {\n video_id\n content\n schema_version\n updatedAt\n edited_at\n auto_chapter_status\n __typename\n }\n ... on EmptyChaptersPayload {\n content\n __typename\n }\n ... on InvalidRequestWarning {\n message\n __typename\n }\n ... on Error {\n message\n __typename\n }\n __typename\n }\n}\n";
        String gql = "query GetSuggestedWorkspaceBanner {\n result: getSuggestedWorkspaceForCurrentUser {\n __typename\n ... on JoinableWorkspace {\n ...SuggestedWorkspaceFragment\n __typename\n }\n }\n}\n\nfragment SuggestedWorkspaceFragment on JoinableWorkspace {\n id\n workspace {\n id\n counts {\n users\n __typename\n }\n name\n workspaceLogoPath\n members: membersConnection(first: 4) {\n nodes {\n id\n member_role\n user {\n id\n first_name\n display_name\n last_name\n avatars {\n thumb\n __typename\n }\n __typename\n }\n __typename\n }\n __typename\n }\n __typename\n }\n autoJoin\n isCurrentUserMember\n requestStatus\n hasPendingInvitation\n __typename\n}\n";
        Parser parser = new Parser();
        Document doc = parser.parseDocument(gql);
        Document doc_temp = parser.parse("{token\n}");

//        return TreeTransformerUtil.changeNode(context, changedNode);


        Node ret = new AstTransformer().transform(doc, new NodeVisitorStub() {

            @Override
            public TraversalControl visitNode(Node node, TraverserContext<Node> context) {
                System.out.println(node.toString());
                return super.visitNode(node, context);
            }
            @Override
            public TraversalControl visitField(Field node, TraverserContext<Node> context) {
                System.out.println(node.getName());
                if (node.getName().equals("avatars")) {
//                    return TreeTransformerUtil.insertAfter(context, parser.parseValue("token"));
                    return TreeTransformerUtil.insertAfter(context, parser.parseValue("temp_placeholder123"));
                } else {
                    return super.visitField(node, context);
                }
            }

        });

        System.out.println(AstPrinter.printAst(doc));
        System.out.println(AstPrinter.printAst(ret).replaceAll("temp_placeholder123", "avatars2 {s1 s2 __typename}"));

//        OperationDefinition definition = (OperationDefinition) doc.getDefinitions().get(0);
//        SelectionSet set = definition.getSelectionSet();
//        Field field = new Field("__type");
//        List<Selection> selectionList = ((OperationDefinition) doc.getDefinitions().get(0)).getSelectionSet().getSelections();
//        ImmutableList.Builder<Selection> builder = new ImmutableList.Builder<>();
//
//        builder.addAll(selectionList).add(field).build();
//        definition.transform()
//        SelectionSet set1 = new SelectionSet(ImmutableList.of(new Field("username")));
//        System.out.println(AstPrinter.printAst(doc)); //which should print out a prettified query here
//        System.out.println(definition.toString());
//        System.out.println(set.toString());
//        System.out.println(doc);

    }

}
