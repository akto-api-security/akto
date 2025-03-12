package com.akto.threat.backend.utils;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import com.akto.log.LoggerMaker.LogDb;
import com.akto.threat.backend.db.AggregateSampleMaliciousEventModel;
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

    public static void sendEvent(AggregateSampleMaliciousEventModel event) {
        // read from config

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
                .url("http://localhost:8088/services/collector")
                .method("POST", body)
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Splunk fbf5060a-81d0-45ed-b98e-39ea6cfe7778")
                .build();
        Response response = null;
        try {
            response =  client.newCall(request).execute();
        } catch (IOException e) {
            System.out.println("Error while executing request " + request.url() + ": " + e.getMessage());
        } finally {
            if (response != null) {
                response.close();
            }
        }
        if (response!= null && response.isSuccessful()) {
            System.out.println("Updated traffic_metrics");
        } else {
            System.out.println("Traffic_metrics not sent");
        }

    }
}
