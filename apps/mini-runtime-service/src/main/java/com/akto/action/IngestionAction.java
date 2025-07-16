package com.akto.action;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

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
import static com.akto.utils.Utility.parseData;

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

    public static Queue<HttpResponseParams> trafficDiscoveryQueue = new ConcurrentLinkedQueue<>();
    private static final int ACCOUNT_ID_TO_ADD_DEFAULT_DATA = getAccountId();

    public String ingestData() {
        try {
            loggerMaker.info("ingestData batch size " + batchData.size());
            for (IngestDataBatch payload: batchData) {
                loggerMaker.info("Inserting data to MRS...");

                // Adding this if we are getting empty method from traffic connector
                if(ACCOUNT_ID_TO_ADD_DEFAULT_DATA == 1745303931 && StringUtils.isEmpty(payload.getMethod())) {
                    payload.setMethod("POST");
                }

                if(ACCOUNT_ID_TO_ADD_DEFAULT_DATA == 1745303931 && StringUtils.isEmpty(payload.getPath())) {
                    payload.setPath("/");
                }

                HttpResponseParams responseParams = parseData(payload);
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
