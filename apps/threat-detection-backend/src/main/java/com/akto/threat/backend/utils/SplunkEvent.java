package com.akto.threat.backend.utils;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.threat.backend.db.AggregateSampleMaliciousEventModel;
import com.akto.threat.backend.db.SplunkIntegrationModel;
import com.akto.util.http_util.CoreHTTPClient;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.mongodb.BasicDBObject;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class SplunkEvent {

    private static final OkHttpClient client = CoreHTTPClient.client.newBuilder()
            .writeTimeout(1, TimeUnit.SECONDS)
            .readTimeout(1, TimeUnit.SECONDS)
            .callTimeout(1, TimeUnit.SECONDS)
            .build();

    private static final LoggerMaker loggerMaker = new LoggerMaker(SplunkEvent.class, LogDb.THREAT_DETECTION);

    public static void sendEvent(AggregateSampleMaliciousEventModel event, SplunkIntegrationModel splunkConfig) {
        if (splunkConfig == null || splunkConfig.getSplunkUrl() == null || splunkConfig.getSplunkUrl() == null) {
            return;
        }
        JSONObject jsonObject = JSON.parseObject(event.getOrig());
        BasicDBObject obj = new BasicDBObject();
        BasicDBObject eventObj = new BasicDBObject();
        eventObj.put("endpoint", event.getUrl());
        eventObj.put("method", event.getMethod().toString());
        eventObj.put("requestPayload", jsonObject.get("requestPayload"));
        eventObj.put("responsePayload", jsonObject.get("responsePayload"));
        eventObj.put("requestHeaders", jsonObject.get("requestHeaders"));
        eventObj.put("responseHeaders", jsonObject.get("responseHeaders"));
        eventObj.put("actor", event.getActor());
        eventObj.put("country", event.getCountry());
        eventObj.put("requestTs", event.getRequestTime());
        obj.put("event", eventObj.toJson());
        obj.put("sourcetype", "_json");


        MediaType mediaType = MediaType.parse("application/json");
        RequestBody body = RequestBody.create(obj.toJson(), mediaType);
        Request request = new Request.Builder()
                .url(splunkConfig.getSplunkUrl())
                .method("POST", body)
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", splunkConfig.getSplunkToken())
                .build();
        Response response = null;
        try {
            response =  client.newCall(request).execute();
        } catch (IOException e) {
            loggerMaker.errorAndAddToDb("Error while executing request " + request.url() + ": " + e.getMessage());
        } finally {
            if (response != null) {
                response.close();
            }
        }
        if (response!= null && response.isSuccessful()) {
            loggerMaker.infoAndAddToDb("Sent splunk event");
        } else {
            loggerMaker.errorAndAddToDb("error sending splunk event " + response);
        }

    }
}
