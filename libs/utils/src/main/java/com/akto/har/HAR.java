package com.akto.har;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.sstoehr.harreader.HarReader;
import de.sstoehr.harreader.HarReaderException;
import de.sstoehr.harreader.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class HAR {
    private final static ObjectMapper mapper = new ObjectMapper();
    private final List<String> errors = new ArrayList<>();
    private static final Logger logger = LoggerFactory.getLogger(Har.class);
    public List<String> getMessages(String harString) throws HarReaderException {
        HarReader harReader = new HarReader();
//        Har har = harReader.readFromFile(new File("/home/avneesh/Downloads/localhost_Archive [21-12-28 01-30-51].har"));
//        Har har = harReader.readFromFile(new File("/home/avneesh/Downloads/localhost_Archive [21-12-28 18-01-36].har"));
        Har har = harReader.readFromString(harString);
        HarLog log = har.getLog();
        List<HarEntry> entries = log.getEntries();

        List<String> entriesList =  new ArrayList<>();
        int idx=0;
        for (HarEntry entry: entries) {
            idx += 1;
            try {
                Map<String,String> result = getResultMap(entry);
                if (result != null) {
                    entriesList.add(mapper.writeValueAsString(result));
                }
            } catch (Exception e) {
                logger.error("Error while parsing har file on entry: " + idx + " ERROR: " + e);
                errors.add("Error in entry " + idx);
            }
        }

        return entriesList;
    }

    public static Map<String,String> getResultMap(HarEntry entry) throws Exception {
        HarRequest request = entry.getRequest();
        HarResponse response = entry.getResponse();
        Date dateTime = entry.getStartedDateTime();

        List<HarHeader> requestHarHeaders = request.getHeaders();
        List<HarHeader> responseHarHeaders = response.getHeaders();

        if (!isApiRequest(responseHarHeaders)) {
            return null;
        }

        Map<String,String> requestHeaderMap = convertHarHeadersToMap(requestHarHeaders);
        Map<String,String> responseHeaderMap = convertHarHeadersToMap(responseHarHeaders);

        Map<String,String> paramMap = mapper.readValue(request.getPostData().getText(), new TypeReference<HashMap<String, String>>() {});
        addQueryStringToMap(request.getQueryString(), paramMap);

        String akto_account_id = 1_000_000 + ""; // TODO:
        String path = getPath(request);
        String requestHeaders = mapper.writeValueAsString(requestHeaderMap);
        String responseHeaders = mapper.writeValueAsString(responseHeaderMap);
        String method = request.getMethod().toString();
        String requestPayload = mapper.writeValueAsString(paramMap);
        String responsePayload = response.getContent().getText();
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

        return result;
    }

    public static boolean isApiRequest(List<HarHeader> headers) {
        String contentType = getContentType(headers);
        if (contentType == null) {
            return false;
        }
        return contentType.contains("application/json");
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

    public static void addQueryStringToMap(List<HarQueryParam> params, Map<String, String> paramsMap) {
        for (HarQueryParam param: params) {
            paramsMap.put(param.getName(), param.getValue());
        }
    }

    public static String getPath(HarRequest request) throws Exception {
        String path = request.getUrl();
        if (path == null) throw new Exception("url path is null in har");
        String[] pathSplit = path.split("\\?");
        if (pathSplit.length != 0) {
            path = pathSplit[0];
        }
        return path;
    }

    public List<String> getErrors() {
        return errors;
    }
}
