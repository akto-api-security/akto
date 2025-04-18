package com.akto.action.gpt.result_fetchers;

import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.http_util.CoreHTTPClient;
import com.mongodb.BasicDBObject;
import java.io.IOException;
import java.util.UUID;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class SimpleResultFetcherStrategy implements ResultFetcherStrategy<BasicDBObject> {

    private static final LoggerMaker logger = new LoggerMaker(SimpleResultFetcherStrategy.class, LogDb.DASHBOARD);;

    @Override
    public BasicDBObject fetchResult(BasicDBObject data) {
        String responseFromLambda = fetchDataFromLambda(data);
        if(!responseFromLambda.isEmpty()){
            logger.debug("Response from lambda: " + responseFromLambda);
            return BasicDBObject.parse(responseFromLambda);
        } else {
            logger.error("Empty response from lambda");
        }
        return null;
    }

    private String fetchDataFromLambda(BasicDBObject data) {
        String requestId = UUID.randomUUID().toString();
        data.put("request_id", requestId);
        logger.debug("Request body:" + data.toJson() );
        OkHttpClient client = CoreHTTPClient.client.newBuilder()
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
