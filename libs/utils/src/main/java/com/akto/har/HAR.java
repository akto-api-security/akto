package com.akto.har;

import com.akto.graphql.GraphQLUtils;
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

    public List<String> getMessages(String harString, int collection_id) throws HarReaderException {
        HarReader harReader = new HarReader();
        Har har = harReader.readFromString(harString, HarReaderMode.LAX);

        HarLog log = har.getLog();
        List<HarEntry> entries = log.getEntries();

        List<String> entriesList =  new ArrayList<>();
        int idx=0;
        for (HarEntry entry: entries) {
            List<OperationDefinition> operationDefinitions = GraphQLUtils.getUtils().parseGraphQLRequest(entry.getRequest().getPostData().getText());
            idx += 1;
            if (operationDefinitions.isEmpty()) {
                updateEntriesList(entry, null, null, null, collection_id, entriesList, idx);
            } else {
                for (OperationDefinition definition : operationDefinitions) {
                    OperationDefinition.Operation operation = definition.getOperation();
                    SelectionSet selectionSets = definition.getSelectionSet();
                    List<Selection> selectionList = selectionSets.getSelections();
                    for (Selection selection : selectionList) {
                        if (selection instanceof Field) {
                            Field field = (Field) selection;
                            updateEntriesList(entry, operation, field.getName(), field, collection_id, entriesList, idx);
                        }
                    }

                }
            }
        }
        return entriesList;
    }

    private void updateEntriesList(HarEntry entry, OperationDefinition.Operation operation, String fieldName, Field field1, int collection_id, List<String> entriesList, int idx) {
        try {
            Map<String,String> result = getResultMap(entry, operation, fieldName, field1);
            logger.info("results map : {}", mapper.writeValueAsString(result));
            result.put("akto_vxlan_id", collection_id +"");
            entriesList.add(mapper.writeValueAsString(result));
        } catch (Exception e) {
            logger.error("Error while parsing har file on entry: " + idx + " ERROR: " + e);
            errors.add("Error in entry " + idx);
        }
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

        String akto_account_id = 1_000_000 + ""; // TODO:
        String path = getPath(request);
        if (operation != null) {
            path += "/" + operation.name().toLowerCase() + "/" + fieldName;
        }

        if (field != null) {
            Map<String, Object> map = GraphQLUtils.getUtils().fieldTraversal(field);
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
        String ip = "null"; // TODO:
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
        // TODO: which will take preference querystring or post value
        for (HarQueryParam param: params) {
            paramsMap.put(param.getName(), param.getValue());
        }
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