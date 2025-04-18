package com.akto.har;

import com.akto.dao.context.Context;
import com.akto.dto.HttpResponseParams;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.sstoehr.harreader.HarReader;
import de.sstoehr.harreader.HarReaderException;
import de.sstoehr.harreader.HarReaderMode;
import de.sstoehr.harreader.model.*;
import java.util.*;

public class HAR {
    private final static ObjectMapper mapper = new ObjectMapper();
    private final List<String> errors = new ArrayList<>();
    private static final LoggerMaker loggerMaker = new LoggerMaker(Har.class);
    public static final String JSON_CONTENT_TYPE = "application/json";
    public static final String FORM_URL_ENCODED_CONTENT_TYPE = "application/x-www-form-urlencoded";
    public List<String> getMessages(String harString, int collection_id, int accountId, HttpResponseParams.Source source) throws HarReaderException {
        HarReader harReader = new HarReader();
        Har har = harReader.readFromString(harString, HarReaderMode.LAX);

        HarLog log = har.getLog();
        List<HarEntry> entries = log.getEntries();

        List<String> entriesList =  new ArrayList<>();
        int idx=0;
        for (HarEntry entry: entries) {
            idx += 1;
            try {
                Map<String,String> result = getResultMap(entry, accountId);
                if (result != null) {
                    result.put("akto_vxlan_id", collection_id+"");
                    result.put("source", source.name());
                    entriesList.add(mapper.writeValueAsString(result));
                }

            } catch (Exception e) {
                loggerMaker.errorAndAddToDb("Error while parsing har file on entry: " + idx + " ERROR: " + e.toString(), LogDb.DASHBOARD);
                errors.add("Error in entry " + idx);
            }
        }

        return entriesList;
    }

    public static Map<String,String> getResultMap(HarEntry entry, int accountId) throws Exception {
        HarRequest request = entry.getRequest();
        HarResponse response = entry.getResponse();

        List<HarHeader> requestHarHeaders = request.getHeaders();
        List<HarHeader> responseHarHeaders = response.getHeaders();

        Map<String,String> requestHeaderMap = convertHarHeadersToMap(requestHarHeaders);
        Map<String,String> responseHeaderMap = convertHarHeadersToMap(responseHarHeaders);

        String requestContentType = getContentType(requestHarHeaders);

        String requestPayload = request.getPostData().getText();
        if (requestPayload == null) requestPayload = "";

        String akto_account_id = accountId + "";
        String urlString = getPath(request);

        String path = urlString;


        String requestHeaders = mapper.writeValueAsString(requestHeaderMap);
        String responseHeaders = mapper.writeValueAsString(responseHeaderMap);
        String method = request.getMethod().toString();
        String responsePayload = response.getContent().getText();;
        String ip = "null"; // TODO:
        String time = Context.now() + "";
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
                if (harHeader.getName().contains(" ")) {
                    continue;
                }
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
