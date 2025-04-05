package com.akto.runtime.utils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.StringJoiner;
import java.util.regex.Pattern;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.akto.dto.HttpRequestParams;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.OriginalHttpResponse;
import com.akto.dto.RawApi;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.mongodb.BasicDBObject;
import static com.akto.dto.RawApi.convertHeaders;

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

    public static String convertOriginalReqRespToString(OriginalHttpRequest request, OriginalHttpResponse response)  {
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

        return ret.toString();
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


}
