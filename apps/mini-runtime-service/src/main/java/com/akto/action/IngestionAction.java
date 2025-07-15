package com.akto.action;

import java.util.*;

import com.akto.dto.HttpRequestParams;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.OriginalHttpRequest;
import com.akto.log.LoggerMaker;
import com.akto.runtime.utils.Utils;
import com.akto.util.HttpRequestResponseUtils;
import com.akto.util.JSONUtils;
import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.mongodb.BasicDBObject;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

import com.akto.utils.IngestDataBatch;
import com.opensymphony.xwork2.Action;
import com.opensymphony.xwork2.ActionSupport;
import org.apache.commons.lang3.math.NumberUtils;
import static com.akto.runtime.utils.Utils.printL;
import static com.akto.runtime.utils.Utils.printUrlDebugLog;

@lombok.Getter
@lombok.Setter
public class IngestionAction extends ActionSupport {
    List<IngestDataBatch> batchData;
    private static final LoggerMaker loggerMaker = new LoggerMaker(IngestionAction.class, LoggerMaker.LogDb.RUNTIME);

    private static int MAX_INFO_PRINT = 500;
    private boolean success;


    private String result;

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public static Queue<HttpResponseParams> trafficDiscoveryQueue = new LinkedList<>();
    private static final int ACCOUNT_ID_TO_ADD_DEFAULT_DATA = getAccountId();
    private static long lastInsertionTime = System.currentTimeMillis();

    public String ingestData() {
        try {
            loggerMaker.info("ingestData batch size " + batchData.size());
            for (IngestDataBatch payload: batchData) {
                loggerMaker.info("Inserting data to kafka...");

                // Adding this if we are getting empty method from traffic connector
                if(ACCOUNT_ID_TO_ADD_DEFAULT_DATA == 1745303931 && StringUtils.isEmpty(payload.getMethod())) {
                    payload.setMethod("POST");
                }

                if(ACCOUNT_ID_TO_ADD_DEFAULT_DATA == 1745303931 && StringUtils.isEmpty(payload.getPath())) {
                    payload.setPath("/");
                }

                HttpResponseParams responseParams = parseIngestDataBatchToHttpResponseParams(payload);
                trafficDiscoveryQueue.add(responseParams);
                result = "success";
                loggerMaker.info("Data has been inserted to Queue in MRS.");
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error while inserting data to Kafka: " + e.getMessage(), LoggerMaker.LogDb.RUNTIME);
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    private final static ObjectMapper mapper = new ObjectMapper();

    public static HttpResponseParams parseIngestDataBatchToHttpResponseParams(IngestDataBatch payloadData) throws JsonProcessingException {

        String method = payloadData.getMethod();
        String url = payloadData.getPath();
        String type = payloadData.getType();
        Map<String,List<String>> requestHeaders = OriginalHttpRequest.buildHeadersMap(payloadData.getRequestHeaders());

        String rawRequestPayload = payloadData.getRequestPayload();
        String requestPayload = HttpRequestResponseUtils.rawToJsonString(rawRequestPayload,requestHeaders);


        String apiCollectionIdStr = (payloadData.getAkto_vxlan_id() != null ? payloadData.getAkto_vxlan_id() : "0");
        int apiCollectionId = 0;
        if (NumberUtils.isDigits(apiCollectionIdStr)) {
            apiCollectionId = NumberUtils.toInt(apiCollectionIdStr, 0);
        }

        HttpRequestParams requestParams = new HttpRequestParams(
                method,url,type, requestHeaders, requestPayload, apiCollectionId
        );

        int statusCode = Integer.parseInt(payloadData.getStatusCode());
        String status = payloadData.getStatus();
        Map<String,List<String>> responseHeaders = OriginalHttpRequest.buildHeadersMap(payloadData.getResponseHeaders());
        String payload = payloadData.getResponsePayload();
        payload = HttpRequestResponseUtils.rawToJsonString(payload, responseHeaders);
        payload = JSONUtils.parseIfJsonP(payload);
        int time = Integer.parseInt(payloadData.getTime());
        String accountId = payloadData.getAkto_account_id();
        String sourceIP = payloadData.getIp();
        String destIP = payloadData.getDestIp() != null ? payloadData.getDestIp() : "";
        String direction = payloadData.getDirection()!= null ? payloadData.getDirection() : "";


        String isPendingStr = payloadData.getIs_pending()!= null ? payloadData.getIs_pending() : "false";
        boolean isPending = !isPendingStr.toLowerCase().equals("false");
        String sourceStr = payloadData.getSource()!= null ? payloadData.getSource() : HttpResponseParams.Source.OTHER.name();
        HttpResponseParams.Source source = HttpResponseParams.Source.valueOf(sourceStr);

        // JSON string of K8 POD tags
        String tags = payloadData.getTag() != null ? payloadData.getTag() : "";
        HttpResponseParams httpResponseParams = new HttpResponseParams(
                type,statusCode, status, responseHeaders, payload, requestParams, time, accountId, isPending, source, mapper.writeValueAsString(payloadData), sourceIP, destIP, direction, tags
        );
        if(!tags.isEmpty()){
            String tagLog = "K8 Pod Tags: " + tags + " Host: " + requestHeaders.getOrDefault("host", new ArrayList<>()) + " Url: " + url;
            printL(tagLog);
            if ((Utils.printDebugHostLog(httpResponseParams) != null) || Utils.printDebugUrlLog(url)) {
                printUrlDebugLog(tagLog);
            }
            injectTagsInHeaders(requestParams, tags);
        }

        return httpResponseParams;
    }

    private static final Gson gson = new Gson();
    private static void injectTagsInHeaders(HttpRequestParams httpRequestParams, String tagsJson){
        if(tagsJson == null || tagsJson.isEmpty()){
            return;
        }

        Map<String, String> tagsMap = gson.fromJson(tagsJson, Map.class);
        for (String tagName: tagsMap.keySet()){
            List<String> headerValues = new ArrayList<>();
            headerValues.add(tagsMap.get(tagName));
            httpRequestParams.getHeaders().put("x-akto-k8s-"+ tagName, headerValues);
        }
        if(Utils.printDebugUrlLog(httpRequestParams.getURL())) {
            printUrlDebugLog("Injecting K8 Pod Tags in Headers: " + tagsMap + " for URL: " + httpRequestParams.getURL());
        }
    }

    public static int getAccountId() {
        try {
            String token = System.getenv("DATABASE_ABSTRACTOR_SERVICE_TOKEN");
            DecodedJWT jwt = JWT.decode(token);
            String payload = jwt.getPayload();
            byte[] decodedBytes = Base64.getUrlDecoder().decode(payload);
            String decodedPayload = new String(decodedBytes);
            BasicDBObject basicDBObject = BasicDBObject.parse(decodedPayload);
            return (int) basicDBObject.getInt("accountId");
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("checkaccount error" + e.getStackTrace());
            return 0;
        }
    }

    public List<IngestDataBatch> getBatchData() {
        return batchData;
    }

    public void setBatchData(List<IngestDataBatch> batchData) {
        this.batchData = batchData;
    }

    public String healthCheck() {
        success = true;
        return Action.SUCCESS.toUpperCase();
    }


}
