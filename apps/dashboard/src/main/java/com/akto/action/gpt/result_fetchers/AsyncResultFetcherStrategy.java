package com.akto.action.gpt.result_fetchers;

import com.mongodb.BasicDBObject;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.UUID;

public class AsyncResultFetcherStrategy implements ResultFetcherStrategy<BasicDBObject> {

    private static final String ASK_GPT_ASYNC_URL = "https://mzt87ut27e.execute-api.ap-south-1.amazonaws.com/test/ask_gpt_async";
    private static final String FETCH_RESPONSE_URL = "https://mzt87ut27e.execute-api.ap-south-1.amazonaws.com/test/fetch_response";

    private static final Logger logger = LoggerFactory.getLogger(AsyncResultFetcherStrategy.class);
    @Override
    public BasicDBObject fetchResult(BasicDBObject data) {
        return fetchDataFromLambda(data);
    }

    private BasicDBObject fetchDataFromLambda(BasicDBObject data) {
        String requestId = UUID.randomUUID().toString();
        data.put("request_id", requestId);
        logger.info("Request body:" + data.toJson());
        OkHttpClient client = new OkHttpClient().newBuilder()
                .writeTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
                .callTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
                .build();
        MediaType mediaType = MediaType.parse("application/json");
        RequestBody body = RequestBody.create(mediaType, new BasicDBObject("data", data).toJson());
        Request request = new Request.Builder()
                .url(ASK_GPT_ASYNC_URL)
                .method("POST", body)
                .addHeader("Content-Type", "application/json")
                .build();
        Response response = null;
        String resp_body = "";
        try {
            response =  client.newCall(request).execute();
            ResponseBody responseBody = response.body();
            if(responseBody != null) {
                resp_body = responseBody.string();
            }
        } catch (IOException e) {
            logger.error("Error while executing request " + request.url() + ": " + e);
        } finally {
            if (response != null) {
                response.close();
            }
        }
        try{
            BasicDBObject responseJson = BasicDBObject.parse(resp_body);
            String status = responseJson.getString("status");
            if(status.equalsIgnoreCase("ACCEPTED")){
                return fetchResponse(requestId);
            }
            BasicDBObject error = new BasicDBObject();
            error.put("error", responseJson.getString("error"));
            return error;
        }catch (Exception e){
            logger.error("Error while parsing response: " + resp_body);
        }
        BasicDBObject error = new BasicDBObject();
        error.put("error", "Something went wrong. Please try again later.");
        return error;
    }

    private BasicDBObject fetchResponse(String requestId) {
        int attempts = 0;
        String status = "";
        while(attempts < 10 && !status.equalsIgnoreCase("READY")){
            logger.info("Attempt: " + attempts + " for request id: " + requestId);
            BasicDBObject data = new BasicDBObject("request_id", requestId);
            OkHttpClient client = new OkHttpClient().newBuilder()
                    .writeTimeout(2, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(2, java.util.concurrent.TimeUnit.SECONDS)
                    .callTimeout(2, java.util.concurrent.TimeUnit.SECONDS)
                    .build();
            MediaType mediaType = MediaType.parse("application/json");
            RequestBody body = RequestBody.create(mediaType, new BasicDBObject("data", data).toJson());
            Request request = new Request.Builder()
                    .url(FETCH_RESPONSE_URL)
                    .method("POST", body)
                    .addHeader("Content-Type", "application/json")
                    .build();
            Response response = null;
            String resp_body = "";
            try {
                response =  client.newCall(request).execute();
                ResponseBody responseBody = response.body();
                if(responseBody != null) {
                    resp_body = responseBody.string();
                }
            } catch (IOException e) {
                logger.error("Error while executing request " + request.url() + ": " + e);
            } finally {
                if (response != null) {
                    response.close();
                }
            }

            BasicDBObject responseJson = BasicDBObject.parse(resp_body);
            logger.info("Response from lambda: {}, attempt #{}", responseJson, attempts);
            status = responseJson.getString("status");

            if(status.equalsIgnoreCase("READY")){
                logger.info("Response from lambda: {}. Found response in {} attempts", responseJson, attempts);
                String response1 = responseJson.getString("response");
                return BasicDBObject.parse(response1);
            }
            attempts++;
            try {
                Thread.sleep(1000 * 5);
            } catch (InterruptedException e) {
                e.printStackTrace();
                logger.info("Error while fetching AktoGPT response: " + e);
            }
        }
        return new BasicDBObject("error", "Timed out while fetching response from AktoGPT");

    }


}
