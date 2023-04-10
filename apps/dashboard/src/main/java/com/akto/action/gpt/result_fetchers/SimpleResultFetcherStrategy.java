package com.akto.action.gpt.result_fetchers;

import com.mongodb.BasicDBObject;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.UUID;

public class SimpleResultFetcherStrategy implements ResultFetcherStrategy<BasicDBObject> {

    private static final Logger logger = LoggerFactory.getLogger(SimpleResultFetcherStrategy.class);

    @Override
    public BasicDBObject fetchResult(BasicDBObject data) {
        String responseFromLambda = fetchDataFromLambda(data);
        if(!responseFromLambda.isEmpty()){
            logger.info("Response from lambda: " + responseFromLambda);
            return BasicDBObject.parse(responseFromLambda);
        } else {
            logger.error("Empty response from lambda");
        }
        return null;
    }

    private String fetchDataFromLambda(BasicDBObject data) {
        String requestId = UUID.randomUUID().toString();
        data.put("request_id", requestId);
        logger.info("Request body:" + data.toJson() );
        OkHttpClient client = new OkHttpClient().newBuilder()
                .writeTimeout(45, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(45, java.util.concurrent.TimeUnit.SECONDS)
                .callTimeout(45, java.util.concurrent.TimeUnit.SECONDS)
                .build();
        MediaType mediaType = MediaType.parse("application/json");
        RequestBody body = RequestBody.create(mediaType, new BasicDBObject("data", data).toJson());
        Request request = new Request.Builder()
                .url("https://18qazon803.execute-api.ap-south-1.amazonaws.com/ask_gpt")
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

        return resp_body;
    }
}
