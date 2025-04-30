package com.akto.action.gpt.result_fetchers;

import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.http_util.CoreHTTPClient;
import com.mongodb.BasicDBObject;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class AsyncResultFetcherStrategy implements ResultFetcherStrategy<BasicDBObject> {

    private static final String ASK_GPT_ASYNC_URL = "https://18qazon803.execute-api.ap-south-1.amazonaws.com/ask_gpt_async";
    private static final String FETCH_RESPONSE_URL = "https://18qazon803.execute-api.ap-south-1.amazonaws.com/fetch_response";

    private static final LoggerMaker logger = new LoggerMaker(AsyncResultFetcherStrategy.class, LogDb.DASHBOARD);

    private static final OkHttpClient client = CoreHTTPClient.client.newBuilder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build();

    @Override
    public BasicDBObject fetchResult(BasicDBObject data) {
        return fetchDataFromLambda(data);
    }

    private BasicDBObject fetchDataFromLambda(BasicDBObject data) {
        String requestId = UUID.randomUUID().toString();
        data.put("request_id", requestId);
        logger.debug("Request body:" + data.toJson());
        MediaType mediaType = MediaType.parse("application/json");
        RequestBody body = RequestBody.create(new BasicDBObject("data", data).toJson(), mediaType);
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
            return fetchResponse(requestId);
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

        while (attempts < 10 && !status.equalsIgnoreCase("READY")) {
            logger.debug("Attempt: {} for request id: {}", attempts, requestId);
            BasicDBObject data = new BasicDBObject("request_id", requestId);
            MediaType mediaType = MediaType.parse("application/json");
            RequestBody body = RequestBody.create(new BasicDBObject("data", data).toJson(), mediaType);
            Request request = new Request.Builder()
                    .url(FETCH_RESPONSE_URL)
                    .post(body)
                    .addHeader("Content-Type", "application/json")
                    .build();

            try (Response response = client.newCall(request).execute()) {
                ResponseBody responseBody = response.body();
                if (responseBody != null) {
                    String resp_body = responseBody.string();
                    BasicDBObject responseJson = BasicDBObject.parse(resp_body);
                    logger.debug("Response from lambda: {}, attempt #{}", responseJson, attempts);
                    status = responseJson.getString("status");

                    if (status.equalsIgnoreCase("READY")) {
                        logger.debug("Response from lambda: {}. Found response in {} attempts", responseJson, attempts);
                        String response1 = responseJson.getString("response");
                        return BasicDBObject.parse(response1);
                    }
                }
            } catch (IOException e) {
                logger.error("Error while executing request {}", request.url(), e);
            }

            attempts++;
            try {
                logger.debug("Sleeping for 5 seconds");
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.error("Error while fetching AktoGPT response", e);
            }
        }
        return new BasicDBObject("error", "Timed out while fetching response from AktoGPT");
    }



}
