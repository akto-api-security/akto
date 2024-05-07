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
            } else if (obj instanceof Object[]) {
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
        return editGraphqlField(payload, field, "", "DELETE");
    }

    public String addGraphqlField(String payload, String field, String value) {
        return editGraphqlField(payload, field, value, "ADD");
    }

    public String modifyGraphqlField(String payload, String field, String value) {
        return editGraphqlField(payload, field, value, "MODIFY");
    }

    private String editGraphqlField(String payload, String field, String value, String type) {
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
            Node result = new AstTransformer().transform(parser.parseDocument(query), new NodeVisitorStub() {

                @Override
                public TraversalControl visitInlineFragment(InlineFragment node, TraverserContext<Node> context) {
                    switch (type) {
                        case "ADD":
                            if (field.equals(node.getTypeCondition().getName())) {

                            }
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
//                                List<Selection> selectionList = node.getSelectionSet().getSelections();
//                                boolean found = false;
//                                for (Selection selection : selectionList) {
//                                    if (selection instanceof Field) {
//                                        if (value.equals(((Field) selection).getName())) {
//                                            found = true;
//                                        }
//                                    }
//                                }
//                                if (!found) {
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
//                                }
//                                return super.visitField(node, context);
                            default:
                                return super.visitField(node, context);
                        }
                    } else {
                        return super.visitField(node, context);
                    }
                }
            });
            String modifiedQuery = AstPrinter.printAst(result);
            if (modifiedQuery.contains(tempVariable)) {
                modifiedQuery = modifiedQuery.replace(tempVariable, value);
            }
            operation.replace("query", modifiedQuery);
        }
        if (payloadObj instanceof Object[]) {
            return gson.toJson(payloadList);
        } else {
            return gson.toJson(payloadList[0]);
        }
    }


    public static void main(String[] args) {
        String query = "[{\"operationName\":\"GetPublishedFolders\",\"variables\":{\"first\":4,\"after\":null,\"parentFolderId\":\"b2b54d60f1b145cba2ad84ee19501799\",\"source\":\"ACTIVE\",\"sortOrder\":\"DESC\",\"sortType\":\"RECENT\",\"filters\":[{\"type\":\"CREATED_BY_ME\"}],\"timeRange\":null},\"query\":\"query GetPublishedFolders($first: Int!, $after: String, $source: FolderSource!, $sourceValue: String, $sortType: LoomsSortType!, $sortOrder: LoomsSortOrder!, $parentFolderId: String, $workspaceId: ID, $filters: [LoomsCollectionFilter!], $timeRange: TimeRange) {\\n  getPublishedFolders {\\n    __typename\\n    ... on GetPublishedFoldersPayload {\\n      folders(\\n        first: $first\\n        after: $after\\n        source: $source\\n        sourceValue: $sourceValue\\n        sortType: $sortType\\n        sortOrder: $sortOrder\\n        parentFolderId: $parentFolderId\\n        workspaceId: $workspaceId\\n        filters: $filters\\n        timeRange: $timeRange\\n      ) {\\n        totalCount\\n        edges {\\n          cursor\\n          node {\\n            ...FolderFragment\\n            __typename\\n          }\\n          __typename\\n        }\\n        pageInfo {\\n          endCursor\\n          hasNextPage\\n          __typename\\n        }\\n        __typename\\n      }\\n      __typename\\n    }\\n  }\\n}\\n\\nfragment FolderFragment on RegularUserFolder {\\n  id\\n  contentLastUpdated\\n  isArchived\\n  currentUserCanEdit\\n  name\\n  organization_id\\n  owner_id\\n  shared\\n  special_id\\n  visibility\\n  totalNestedVideos\\n  isTopLevelFolder\\n  hasSubFolders\\n  personalizedVideo {\\n    id\\n    __typename\\n  }\\n  space {\\n    id\\n    name\\n    is_primary\\n    __typename\\n  }\\n  owner {\\n    email\\n    first_name\\n    last_name\\n    id\\n    avatars {\\n      name\\n      thumb\\n      __typename\\n    }\\n    __typename\\n  }\\n  organization {\\n    id\\n    name\\n    __typename\\n  }\\n  parent_folder {\\n    id\\n    name\\n    special_id\\n    owner_id\\n    owner {\\n      email\\n      first_name\\n      last_name\\n      __typename\\n    }\\n    __typename\\n  }\\n  __typename\\n}\\n\"},{\"operationName\":\"GetPublishedFolders\",\"variables\":{\"first\":4,\"after\":null,\"source\":\"ACTIVE\",\"sortOrder\":\"DESC\",\"sortType\":\"RECENT\",\"filters\":[{\"type\":\"CREATED_BY_ME\"}],\"timeRange\":null},\"query\":\"query GetPublishedFolders($first: Int!, $after: String, $source: FolderSource!, $sourceValue: String, $sortType: LoomsSortType!, $sortOrder: LoomsSortOrder!, $parentFolderId: String, $workspaceId: ID, $filters: [LoomsCollectionFilter!], $timeRange: TimeRange) {\\n  getPublishedFolders {\\n    __typename\\n    ... on GetPublishedFoldersPayload {\\n      folders(\\n        first: $first\\n        after: $after\\n        source: $source\\n        sourceValue: $sourceValue\\n        sortType: $sortType\\n        sortOrder: $sortOrder\\n        parentFolderId: $parentFolderId\\n        workspaceId: $workspaceId\\n        filters: $filters\\n        timeRange: $timeRange\\n      ) {\\n        totalCount\\n        edges {\\n          cursor\\n          node {\\n            ...FolderFragment\\n            __typename\\n          }\\n          __typename\\n        }\\n        pageInfo {\\n          endCursor\\n          hasNextPage\\n          __typename\\n        }\\n        __typename\\n      }\\n      __typename\\n    }\\n  }\\n}\\n\\nfragment FolderFragment on RegularUserFolder {\\n  id\\n  contentLastUpdated\\n  isArchived\\n  currentUserCanEdit\\n  name\\n  organization_id\\n  owner_id\\n  shared\\n  special_id\\n  visibility\\n  totalNestedVideos\\n  isTopLevelFolder\\n  hasSubFolders\\n  personalizedVideo {\\n    id\\n    __typename\\n  }\\n  space {\\n    id\\n    name\\n    is_primary\\n    __typename\\n  }\\n  owner {\\n    email\\n    first_name\\n    last_name\\n    id\\n    avatars {\\n      name\\n      thumb\\n      __typename\\n    }\\n    __typename\\n  }\\n  organization {\\n    id\\n    name\\n    __typename\\n  }\\n  parent_folder {\\n    id\\n    name\\n    special_id\\n    owner_id\\n    owner {\\n      email\\n      first_name\\n      last_name\\n      __typename\\n    }\\n    __typename\\n  }\\n  __typename\\n}\\n\"},{\"operationName\":\"GetUserByIdWithProfile\",\"variables\":{\"userId\":\"28411178\"},\"query\":\"query GetUserByIdWithProfile($userId: ID!) {\\n  user: getUserById(userId: $userId) {\\n    __typename\\n    ... on RegularUserPayload {\\n      user {\\n        id\\n        email\\n        first_name\\n        last_name\\n        avatars {\\n          thumb\\n          large\\n          __typename\\n        }\\n        profile {\\n          profileUrl\\n          profileVideoCount\\n          communityVideoCount\\n          profileInfo {\\n            role\\n            location\\n            __typename\\n          }\\n          __typename\\n        }\\n        __typename\\n      }\\n      __typename\\n    }\\n    ... on CommunityUserPayload {\\n      user {\\n        id\\n        first_name\\n        last_name\\n        avatars {\\n          thumb\\n          large\\n          __typename\\n        }\\n        profile {\\n          profileUrl\\n          communityVideoCount\\n          profileInfo {\\n            role\\n            location\\n            __typename\\n          }\\n          __typename\\n        }\\n        __typename\\n      }\\n      __typename\\n    }\\n  }\\n}\\n\"}]";
        GraphQLUtils.getUtils().editGraphqlField(query, "GetPublishedFoldersPayload", "email", "ADD");
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
