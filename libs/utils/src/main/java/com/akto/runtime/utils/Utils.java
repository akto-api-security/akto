package com.akto.runtime.utils;

import java.io.BufferedReader;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;

import com.akto.dto.HttpRequestParams;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.OriginalHttpResponse;
import com.akto.dto.RawApi;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.mongodb.BasicDBObject;
import static com.akto.dto.RawApi.convertHeaders;

public class Utils {
    private static final LoggerMaker logger = new LoggerMaker(Utils.class, LogDb.RUNTIME);

    private static int debugPrintCounter = 500;
    public static void printL(Object o) {
        if (debugPrintCounter > 0) {
            debugPrintCounter--;
            logger.warn(o.toString());
        }
    }

    public static void printUrlDebugLog(Object o){
        if( o == null ){
            return;
        }
        logger.infoAndAddToDb(o.toString());
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

    private static volatile Set<String> DEBUG_HOSTS_SET = initializeDebugHostsSet();
    private static volatile Set<String> DEBUG_URLS_SET = initializeDebugUrlsSet();
    private static final String DEBUG_URLS_FILE_PATH = "/app/debug-urls.txt";
    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    static {
        startDebugUrlsFileWatcher();
    }
    private static volatile Set<String> lastFileUrls = null;

    private static void startDebugUrlsFileWatcher() {
        scheduler.scheduleAtFixedRate(() -> {
                try {
                    Set<String> fileUrls = readDebugUrlsFromFile();
                    if (fileUrls != null && !fileUrls.isEmpty()) {
                        if (lastFileUrls == null || !lastFileUrls.equals(fileUrls)) {
                            DEBUG_URLS_SET = fileUrls;
                            DEBUG_HOSTS_SET = fileUrls;
                            logger.infoAndAddToDb("DEBUG_URLS updated from file: " + DEBUG_URLS_SET.toString());
                            lastFileUrls = new HashSet<>(fileUrls);
                        }
                    }
                } catch (Exception e) {
                    logger.errorAndAddToDb(e, "Failed to read debug URLs from file: " + e.getMessage());
                }
        }, 0, 30, TimeUnit.SECONDS);
    }

    private static Set<String> readDebugUrlsFromFile() {
        File file = new File(DEBUG_URLS_FILE_PATH);
        if (!file.exists()) {
            return null;
        }
        try (BufferedReader br = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            Set<String> urls = new HashSet<>();
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) {
                    urls.add(line);
                }
            }
            return urls;
        } catch (Exception e) {
            logger.errorAndAddToDb(e, "Error reading debug URLs file: " + e.getMessage());
            return null;
        }
    }

    private static Set<String> initializeDebugHostsSet() {
        String debugHosts = System.getenv("DEBUG_HOSTS");
        if (debugHosts == null || debugHosts.isEmpty()) {
            return new HashSet<>();
        }
        return new HashSet<>(Arrays.asList(debugHosts.split(",")));
    }

    public static String printDebugHostLog(HttpResponseParams httpResponseParams) {
        if (DEBUG_HOSTS_SET.isEmpty()) return null;
        if (httpResponseParams == null || httpResponseParams.requestParams == null || httpResponseParams.requestParams.getHeaders() == null) {
            return null;
        }
        Map<String, List<String>> headers = httpResponseParams.getRequestParams().getHeaders();
        List<String> hosts = headers.get("host");
        if (hosts == null || hosts.isEmpty()) return null;
        String host = hosts.get(0);

        return DEBUG_HOSTS_SET.contains(host) ? host : null;
    }

    private static Set<String> initializeDebugUrlsSet() {
        Set<String> ret = new HashSet<>();

        String debugUrls = System.getenv("DEBUG_URLS");
        if (debugUrls == null || debugUrls.isEmpty()) {
            ret = new HashSet<>();
        } else {
            ret = new HashSet<>(Arrays.asList(debugUrls.split(",")));
        }
        logger.info("DEBUG_URLS initialized with: " + ret.toString());
        return ret;
    }

    public static boolean printDebugUrlLog(String url) {
        if (DEBUG_URLS_SET.isEmpty())
            return false;
        if (url == null || url.isEmpty())
            return false;
        if (DEBUG_URLS_SET.isEmpty())
            return false;
        for (String debugUrl : DEBUG_URLS_SET) {
            if (url.contains(debugUrl)) {
                return true;
            }
        }
        return false;
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
