package com.akto.runtime.utils;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.StringJoiner;
import java.util.regex.Pattern;

import org.apache.commons.lang3.math.NumberUtils;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.akto.dto.HttpRequestParams;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.OriginalHttpResponse;
import com.akto.dto.RawApi;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.Constants;
import com.akto.util.HttpRequestResponseUtils;
import com.akto.util.JSONUtils;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.mongodb.BasicDBObject;
import static com.akto.dto.RawApi.convertHeaders;

import static com.akto.util.HttpRequestResponseUtils.GRPC_CONTENT_TYPE;

public class Utils {
    private static final Logger logger = LoggerFactory.getLogger(Utils.class);

    private static int debugPrintCounter = 500;
    public static void printL(Object o) {
        if (debugPrintCounter > 0) {
            debugPrintCounter--;
            logger.info(o.toString());
        }
    }

    public static Properties configProperties(String kafkaBrokerUrl, String groupIdConfig, int maxPollRecordsConfig) {
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBrokerUrl);
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, maxPollRecordsConfig);
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, groupIdConfig);
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

        return properties;
    }

    public static String convertOriginalReqRespToString(OriginalHttpRequest request, OriginalHttpResponse response, int responseTime)  {
        BasicDBObject ret = convertOriginalReqRespToStringUtil(request, response);
        ret.append("responseTime", responseTime);
        return ret.toString();
    }

    public static String convertOriginalReqRespToString(OriginalHttpRequest request, OriginalHttpResponse response)  {
        return convertOriginalReqRespToStringUtil(request, response).toString();
    }

    public static BasicDBObject convertOriginalReqRespToStringUtil(OriginalHttpRequest request, OriginalHttpResponse response)  {
        BasicDBObject req = new BasicDBObject();
        if (request != null) {
            req.put("url", request.getUrl());
            req.put("method", request.getMethod());
            req.put("type", request.getType());
            req.put("queryParams", request.getQueryParams());
            req.put("body", request.getBody());
            req.put("headers", convertHeaders(request.getHeaders()));
        }

        BasicDBObject resp = new BasicDBObject();
        if (response != null) {
            resp.put("statusCode", response.getStatusCode());
            resp.put("body", response.getBody());
            resp.put("headers", convertHeaders(response.getHeaders()));
        }

        BasicDBObject ret = new BasicDBObject();
        ret.put("request", req);
        ret.put("response", resp);

        return ret;
    }

    public static String convertToSampleMessage(String message) throws Exception {
        JSONObject jsonObject = JSON.parseObject(message);
        JSONObject request = (JSONObject) jsonObject.get("request");
        JSONObject response = (JSONObject) jsonObject.get("response");

        JSONObject sampleMessage = new JSONObject();
        if(request != null) {
            if(request.get("body") != null) {
                sampleMessage.put("requestPayload", request.get("body"));
            }
            if(request.get("headers") != null) {
                sampleMessage.put("requestHeaders", request.get("headers"));
            }
            // TODO: add query params to url
            if(request.get("url") != null) {
                sampleMessage.put("path", request.get("url"));
            }
            if(request.get("method") != null) {
                sampleMessage.put("method", request.get("method"));
            }
            if(request.get("type") != null) {
                sampleMessage.put("type", request.get("type"));
            }
        }
        if(response != null) {
            if(response.get("body") != null) {
                sampleMessage.put("responsePayload", response.get("body"));
            }
            if(response.get("headers") != null) {
                sampleMessage.put("responseHeaders", response.get("headers"));
            }
            if(response.get("statusCode") != null) {
                sampleMessage.put("statusCode", (Integer)response.getInteger("statusCode"));
            }

        }
        return sampleMessage.toJSONString();
    }

    public static Map<String,String> parseCookie(List<String> cookieList){
        Map<String,String> cookieMap = new HashMap<>();
        if(cookieList==null)return cookieMap;
        for (String cookieValues : cookieList) {
            String[] cookies = cookieValues.split(";");
            for (String cookie : cookies) {
                cookie=cookie.trim();
                String[] cookieFields = cookie.split("=");
                boolean twoCookieFields = cookieFields.length == 2;
                if (twoCookieFields) {
                    if(!cookieMap.containsKey(cookieFields[0])){
                        cookieMap.put(cookieFields[0], cookieFields[1]);
                    }
                }
            }
        }
        return cookieMap;
    }

    private static int GRPC_DEBUG_COUNTER = 50;

    public static HttpResponseParams parseKafkaMessage(String message) throws Exception {

        //convert java object to JSON format

        JSONObject jsonObject = JSON.parseObject(message);

        String method = jsonObject.getString("method");
        String url = jsonObject.getString("path");
        String type = jsonObject.getString("type");
        Map<String,List<String>> requestHeaders = OriginalHttpRequest.buildHeadersMap(jsonObject, "requestHeaders");

        String rawRequestPayload = jsonObject.getString("requestPayload");
        String requestPayload = null;
        Map<String,String> decryptedRequestPayload = HttpRequestResponseUtils.decryptRequestPayload(rawRequestPayload);
        if(!decryptedRequestPayload.isEmpty() && decryptedRequestPayload.get("type") != null){
            requestPayload = decryptedRequestPayload.get("payload");
            logger.info("decrypted request payload: " + requestPayload,LogDb.RUNTIME);
            requestHeaders.put(
                Constants.AKTO_DECRYPT_HEADER,
                Arrays.asList(decryptedRequestPayload.get("type"))
            );   
        }else{
            requestPayload = HttpRequestResponseUtils.rawToJsonString(rawRequestPayload,requestHeaders);
        }
         
        if (GRPC_DEBUG_COUNTER > 0) {
            String acceptableContentType = HttpRequestResponseUtils.getAcceptableContentType(requestHeaders);
            if (acceptableContentType != null && rawRequestPayload.length() > 0) {
                // only if request payload is of FORM_URL_ENCODED_CONTENT_TYPE we convert it to json
                if (acceptableContentType.equals(GRPC_CONTENT_TYPE)) {
                    logger.info("grpc kafka payload:" + message,LogDb.RUNTIME);
                    GRPC_DEBUG_COUNTER--;
                }
            }
        }

        String apiCollectionIdStr = jsonObject.getOrDefault("akto_vxlan_id", "0").toString();
        int apiCollectionId = 0;
        if (NumberUtils.isDigits(apiCollectionIdStr)) {
            apiCollectionId = NumberUtils.toInt(apiCollectionIdStr, 0);
        }

        HttpRequestParams requestParams = new HttpRequestParams(
                method,url,type, requestHeaders, requestPayload, apiCollectionId
        );

        int statusCode = jsonObject.getInteger("statusCode");
        String status = jsonObject.getString("status");
        Map<String,List<String>> responseHeaders = OriginalHttpRequest.buildHeadersMap(jsonObject, "responseHeaders");
        String payload = jsonObject.getString("responsePayload");
        payload = HttpRequestResponseUtils.rawToJsonString(payload, responseHeaders);
        payload = JSONUtils.parseIfJsonP(payload);
        int time = jsonObject.getInteger("time");
        String accountId = jsonObject.getString("akto_account_id");
        String sourceIP = jsonObject.getString("ip");
        String destIP = jsonObject.getString("destIp");
        String direction = jsonObject.getString("direction");

        String isPendingStr = (String) jsonObject.getOrDefault("is_pending", "false");
        boolean isPending = !isPendingStr.toLowerCase().equals("false");
        String sourceStr = (String) jsonObject.getOrDefault("source", HttpResponseParams.Source.OTHER.name());
        HttpResponseParams.Source source = HttpResponseParams.Source.valueOf(sourceStr);
        
        return new HttpResponseParams(
                type,statusCode, status, responseHeaders, payload, requestParams, time, accountId, isPending, source, message, sourceIP, destIP, direction
        );
    }

    public static Pattern createRegexPatternFromList(List<String> discardedUrlList){
        StringJoiner joiner = new StringJoiner("|", ".*\\.(", ")(\\?.*)?");
        for (String extension : discardedUrlList) {
            if(extension.startsWith("CONTENT-TYPE")){
                continue;
            }
            joiner.add(extension);
        }
        String regex = joiner.toString();

        Pattern pattern = Pattern.compile(regex);
        return pattern;
    }

    public static HttpResponseParams convertRawApiToHttpResponseParams(RawApi rawApi, HttpResponseParams originalHttpResponseParams){

        HttpRequestParams ogRequestParams = originalHttpResponseParams.getRequestParams();
        OriginalHttpRequest modifiedRequest = rawApi.getRequest();

        ogRequestParams.setHeaders(modifiedRequest.getHeaders());
        ogRequestParams.setUrl(modifiedRequest.getFullUrlWithParams());
        ogRequestParams.setPayload(modifiedRequest.getBody());

        originalHttpResponseParams.setRequestParams(ogRequestParams);

        return originalHttpResponseParams;
    }

    public static String convertEpochToDateTime(int epoch) {
        if (epoch <= 0) {
            return "Invalid epoch time";
        }
        ZonedDateTime dateTime = Instant.ofEpochSecond(epoch)
                .atZone(ZoneId.systemDefault());
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd MMM yyyy, h:mm a");
        return dateTime.format(formatter);
    }


}
