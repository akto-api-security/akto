package com.akto.action;

import java.util.*;

import com.akto.dto.HttpRequestParams;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.OriginalHttpRequest;
import com.akto.log.LoggerMaker;
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

    private static Queue<HttpResponseParams> queue = new LinkedList<>();
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

               // KafkaUtils.insertData(payload);
                HttpResponseParams responseParams = parseIngestDataBatchToHttpResponseParams(payload);
                queue.add(responseParams);

                long currentTime = System.currentTimeMillis();
                com.akto.hybrid_runtime.Main.processData(queue);
                // Check if queue size > 100 or 5 seconds have passed since last insertion
//                if (queue.size() > 100 || (currentTime - lastInsertionTime > 5000 && queue.size() >= 100)) {
//                    com.akto.hybrid_runtime.Main.processData(queue);
//                    queue.clear();
//                    lastInsertionTime = currentTime;
//                } else {
//                    lastInsertionTime = currentTime;
//                }

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

    public static HttpResponseParams parseIngestDataBatchToHttpResponseParams(IngestDataBatch payload) throws JsonProcessingException {
        HttpRequestParams requestParams = new HttpRequestParams();
        requestParams.setMethod(payload.getMethod());
        requestParams.setUrl(payload.getPath());

      //  requestParams.setBody(payload.getRequestBody());

        HttpResponseParams responseParams = new HttpResponseParams();
        responseParams.setRequestParams(requestParams);
        responseParams.setPayload(payload.getResponsePayload());
        responseParams.setStatusCode(Integer.parseInt(payload.getStatusCode()));
        responseParams.setAccountId(payload.getAkto_account_id());
        responseParams.setSourceIP(payload.getIp());
        responseParams.setDestIP(payload.getDestIp());
        responseParams.setTime(Integer.parseInt(payload.getTime()));
        responseParams.setType(payload.getType());


        Map<String,List<String>> requestHeaders = OriginalHttpRequest.buildHeadersMap(payload.getRequestHeaders());
        Map<String, List<String>> responseHeaders = OriginalHttpRequest.buildHeadersMap(payload.getResponseHeaders());
        requestHeaders.put("x-forwarded-for", Collections.singletonList(payload.getIp()));

        Map<String, List<String>> mergedHeaders = new HashMap<>(requestHeaders);
        responseHeaders.forEach((key, value) -> mergedHeaders.merge(key, value, (v1, v2) -> {
            List<String> mergedList = new ArrayList<>(v1);
            mergedList.addAll(v2);
            return mergedList;
        }));
        responseParams.setHeaders(mergedHeaders);

        return responseParams;
    }

    public static int getAccountId() {
        try {
            String token = "eyJhbGciOiJSUzI1NiJ9.eyJpc3MiOiJBa3RvIiwic3ViIjoiaW52aXRlX3VzZXIiLCJhY2NvdW50SWQiOjE3NDg4MDU3NTQsImlhdCI6MTc1MjQ5NzA4NiwiZXhwIjoxNzY4Mzk0Njg2fQ.Z8tu1eOv9LQVpiMQp6L_P-VIyNtODAJIUtNWNOidwZ-AFcj1SBtJTc0G05UAoVxOCE1MsZQwOpbLB3Ci_b7S7OYco40opx3GumKLmOcTChplvGR8tMOEjut7B-j--y99r9g0i0UhCiuk64yDmVjBcLKRiX_0J9GBgMcy14lkFeIQQ5o6FnAXxQd27agireZPR6qQb0PZku5V_b-5E9Am65cLwmUmqyRzSshn1aL1RgXUb1Fo93x-XLtyn4UpYgSvCZEAyhzKMRc9HDH4UvW8Z44ctm25rGb8OAklTrhwfsoNQZoDPgoiffayCBFzEt1x9wmzCy3U0zFNZdiZgGyOPw";
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
