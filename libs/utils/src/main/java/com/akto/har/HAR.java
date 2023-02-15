package com.akto.har;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import de.sstoehr.harreader.HarReader;
import de.sstoehr.harreader.HarReaderException;
import de.sstoehr.harreader.HarReaderMode;
import de.sstoehr.harreader.model.*;
import graphql.language.*;
import graphql.parser.Parser;
import graphql.validation.DocumentVisitor;
import graphql.validation.LanguageTraversal;
import org.mortbay.util.ajax.JSON;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class HAR {
    private final static ObjectMapper mapper = new ObjectMapper();
    private final List<String> errors = new ArrayList<>();
    private static final Logger logger = LoggerFactory.getLogger(Har.class);
    public static final String JSON_CONTENT_TYPE = "application/json";
    public static final String FORM_URL_ENCODED_CONTENT_TYPE = "application/x-www-form-urlencoded";
    public static final String __ARGS = "__args";

    public List<String> getMessages(String harString, int collection_id) throws HarReaderException {
        HarReader harReader = new HarReader();
        Har har = harReader.readFromString(harString, HarReaderMode.LAX);

        HarLog log = har.getLog();
        List<HarEntry> entries = log.getEntries();

        List<String> entriesList =  new ArrayList<>();
        int idx=0;
        for (HarEntry entry: entries) {
            List<OperationDefinition> operationDefinitions = parseGraphQLRequest(entry.getRequest().getPostData().getText());
            idx += 1;
            if (operationDefinitions.isEmpty()) {
                try {
                    Map<String,String> result = getResultMap(entry, null, null, null);
                    logger.info("results map : {}", mapper.writeValueAsString(result));
                    result.put("akto_vxlan_id", collection_id+"");
                    entriesList.add(mapper.writeValueAsString(result));
                } catch (Exception e) {
                    logger.error("Error while parsing har file on entry: " + idx + " ERROR: " + e);
                    errors.add("Error in entry " + idx);
                }
            } else {
                for (OperationDefinition definition : operationDefinitions) {
                    OperationDefinition.Operation operation = definition.getOperation();
                    SelectionSet selectionSets = definition.getSelectionSet();
                    List<Selection> selectionList = selectionSets.getSelections();
                    for (Selection selection : selectionList) {
                        if (selection instanceof Field) {
                            Field field = (Field) selection;
                            try {
                                Map<String,String> result = getResultMap(entry, operation, field.getName(), field);
                                logger.info("results map : {}", mapper.writeValueAsString(result));
                                result.put("akto_vxlan_id", collection_id+"");
                                entriesList.add(mapper.writeValueAsString(result));
                            } catch (Exception e) {
                                logger.error("Error while parsing har file on entry: " + idx + " ERROR: " + e);
                                errors.add("Error in entry " + idx);
                            }
                        }
                    }

                }
            }
        }
        return entriesList;
    }

    private List<OperationDefinition> parseGraphQLRequest(String requestPayload) {
        List<OperationDefinition> result = new ArrayList<>();
        Object obj  = JSON.parse(requestPayload);
        if (obj instanceof HashMap) {
            HashMap map = (HashMap) obj;
            String query = (String) map.get("query");
            try {
                Document document = Parser.parse(query);
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

    public static Map<String,String> getResultMap(HarEntry entry,
                                                  OperationDefinition.Operation operation,
                                                  String fieldName, Field field) throws Exception {
        HarRequest request = entry.getRequest();
        HarResponse response = entry.getResponse();
        Date dateTime = entry.getStartedDateTime();

        List<HarHeader> requestHarHeaders = request.getHeaders();
        List<HarHeader> responseHarHeaders = response.getHeaders();

        Map<String,String> requestHeaderMap = convertHarHeadersToMap(requestHarHeaders);
        Map<String,String> responseHeaderMap = convertHarHeadersToMap(responseHarHeaders);

        String requestPayload = request.getPostData().getText();

        if (requestPayload == null) requestPayload = "";

        String akto_account_id = 1_000_000 + "";
        String path = getPath(request);
        if (operation != null) {
            path += "/" + operation.name().toLowerCase() + "/" + fieldName;
        }

        if (field != null) {
            HashMap<String, Object> map = new HashMap<>();
            new LanguageTraversal().traverse(field, new DocumentVisitor() {
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

            Object obj = JSON.parse(requestPayload);
            if (obj instanceof Map) {
                Map tempMap = (Map) obj;
                for (String key : map.keySet()) {
                    tempMap.put(key.substring(1), map.get(key));
                }
                requestPayload = JSON.toString(obj);
            }
        }

        String requestHeaders = mapper.writeValueAsString(requestHeaderMap);
        String responseHeaders = mapper.writeValueAsString(responseHeaderMap);
        String method = request.getMethod().toString();
        String responsePayload = response.getContent().getText();;
        String ip = "null"; 
        String time = (int) (dateTime.getTime() / 1000) + "";
        String statusCode = response.getStatus() + "";
        String type = request.getHttpVersion();
        String status = response.getStatusText();
        String contentType = getContentType(responseHarHeaders);

        Map<String,String> result = new HashMap<>();
        result.put("akto_account_id",akto_account_id);
        result.put("path",path);
        result.put("requestHeaders",requestHeaders);
        result.put("responseHeaders",responseHeaders);
        result.put("method",method);
        result.put("requestPayload",requestPayload);
        result.put("responsePayload",responsePayload);
        result.put("ip",ip);
        result.put("time",time);
        result.put("statusCode",statusCode);
        result.put("type",type);
        result.put("status",status);
        result.put("contentType",contentType);
        result.put("source", "HAR");

        return result;
    }

    public static boolean isApiRequest(List<HarHeader> headers) {
        String contentType = getContentType(headers);
        if (contentType == null) {
            return false;
        }
        return contentType.contains(JSON_CONTENT_TYPE);
     }

     public static String getContentType(List<HarHeader> headers) {
         for (HarHeader harHeader: headers) {
             if (harHeader.getName().equalsIgnoreCase("content-type")) {
                 return harHeader.getValue();
             }
         }
         return null;
     }

     public static Map<String,String> convertHarHeadersToMap(List<HarHeader> headers) {
        Map<String,String> headerMap = new HashMap<>();

        for (HarHeader harHeader: headers) {
            if (harHeader != null) {
                headerMap.put(harHeader.getName(), harHeader.getValue());
            }
        }

        return headerMap;
     }

    public static void addQueryStringToMap(List<HarQueryParam> params, Map<String,Object> paramsMap) {
        for (HarQueryParam param: params) {
            paramsMap.put(param.getName(), param.getValue());
        }
    }

    public static void main(String[] args) {
        Parser parser = new Parser();
//        Document document = parser.parseDocument("{ allBooks { id author { firstName } }  allAuthors { firstName lastName } authorById(id: \"author-1\") { id firstName lastName bookList{ name } }}");
        String request = "{\"query\":\"{\\n  allBooks {\\n  \\tid\\n    author {\\n      firstName\\n    }\\n  }\\n  \\n  allAuthors {\\n    firstName\\n    lastName\\n  }\\n  authorById(id: \\\"author-1\\\") {\\n    id\\n    firstName\\n    lastName\\n    bookList{\\n      name\\n    }\\n  }\\n}\"}";
        String request2 = "{ __schema { types { name } } shivamBook : bookById(id: \"book-1\") { id name } allBooks{ name author { firstName } } bookById(id: \"book-2\") { id name } allBooks{ name author { firstName } } allAuthors { id firstName lastName } authorById(id: \"author-1\") { lastName bookList { name } } }";
        String response = "{\"data\":{\"allBooks\":[{\"id\":\"book-1\",\"author\":{\"firstName\":\"Joanne\"}},{\"id\":\"book-2\",\"author\":{\"firstName\":\"Herman\"}},{\"id\":\"book-4\",\"author\":{\"firstName\":\"Harper\"}},{\"id\":\"book-3\",\"author\":{\"firstName\":\"Anne\"}}],\"allAuthors\":[{\"firstName\":\"Joanne\",\"lastName\":\"Rowling\"},{\"firstName\":\"Herman\",\"lastName\":\"Melville\"},{\"firstName\":\"Harper\",\"lastName\":\"Lee\"},{\"firstName\":\"Anne\",\"lastName\":\"Rice\"},{\"firstName\":\"Shivam\",\"lastName\":\"Rawat\"}],\"authorById\":{\"id\":\"author-1\",\"firstName\":\"Joanne\",\"lastName\":\"Rowling\",\"bookList\":[{\"name\":\"Harry Potter and the Philosopher's Stone\"}]}}}";
        Object obj  = JSON.parse(request);
        System.out.println(request);
        HashMap map = (HashMap) obj;
        String query = (String) map.get("query");
        Document document = parser.parseDocument(request2);
        List<Definition> definitionList = document.getDefinitions();
        System.out.println(obj);
        System.out.println(document);
        for (Definition definition : definitionList) {
            Gson gson = new Gson();
//            String json = gson.toJson(definition);

            SelectionSet selectionSets = ((OperationDefinition) definition).getSelectionSet();
            List<Selection> selectionList = selectionSets.getSelections();
            for (Selection selection : selectionList) {
                if (selection instanceof Field) {
                    Field field = (Field) selection;
                    String json = gson.toJson(field);
                    System.out.println(json);
                }
            }


//            System.out.println(((OperationDefinition) definition).getOperation());
//            System.out.println(json);
        }

        ProcessBuilder processBuilder = new ProcessBuilder();
    }

    public static String getPath(HarRequest request) throws Exception {
        String path = request.getUrl();
        if (path == null) throw new Exception("url path is null in har");
        return path;
    }

    public List<String> getErrors() {
        return errors;
    }
}
