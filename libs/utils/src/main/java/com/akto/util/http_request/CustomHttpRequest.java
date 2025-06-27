package com.akto.util.http_request;

import com.akto.dao.common.LoggerMaker;
import com.akto.dao.common.LoggerMaker.LogDb;
import com.akto.util.http_util.CoreHTTPClient;
import com.google.gson.Gson;
import com.mongodb.BasicDBObject;

import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.apache.http.*;
import org.apache.http.client.HttpResponseException;
import java.util.HashMap;
import java.util.Map;

public class CustomHttpRequest {
    private static final OkHttpClient httpClient = CoreHTTPClient.client.newBuilder().build();
    private static final LoggerMaker loggerMaker = new LoggerMaker(CustomHttpRequest.class, LogDb.DASHBOARD);

    public static final String FORM_URL_ENCODED_CONTENT_TYPE = "application/x-www-form-urlencoded";

    public static Map<String, Object> getRequest(String url, String authHeader) throws HttpResponseException {
        Request request = new Request.Builder()
                .url(url)
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .header(HttpHeaders.AUTHORIZATION, authHeader)
                .build();

        return s(request);
    }

    public static RequestBody createFormEncodedRequestBody(BasicDBObject params) {
        FormBody.Builder formBuilder = new FormBody.Builder();
        if (params != null) {
            for (Map.Entry<String, Object> param : params.entrySet()) {
                formBuilder.addEncoded(param.getKey(), param.getValue().toString());
            }
        }
        return formBuilder.build();
    }

    public static Map<String, Object> postRequest(String url, BasicDBObject params) throws HttpResponseException {
        RequestBody requestBody = createFormEncodedRequestBody(params);
        Request request = new Request.Builder()
                .url(url)
                .post(requestBody)
                .header("Accept", "application/json")
                .build();

        return s(request);
    }

    public static Map<String, Object> postRequestEncodedType(String url, BasicDBObject params)
            throws HttpResponseException {
        RequestBody requestBody = createFormEncodedRequestBody(params);
        Request request = new Request.Builder()
                .url(url)
                .post(requestBody)
                .header("Accept", "application/json")
                .header("Content-Type", FORM_URL_ENCODED_CONTENT_TYPE)
                .build();

        return s(request);
    }

    public static Map<String, Object> s(Request request) throws HttpResponseException {
        //Execute and get the response.
        Response response = null;
        Map<String, Object> jsonMap = new HashMap<>();
        try {
            response = httpClient.newCall(request).execute();
            if (response == null) {
                loggerMaker.infoAndAddToDb("response null");
                return null;
            }
            if (!response.isSuccessful()) {
                throw new HttpResponseException(response.code(), response.message());
            }

            String jsonData = response.body().string();
            jsonMap = new Gson().fromJson(jsonData, Map.class);

        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "error in sending requests in SSO auth");
        } finally {
            if (response != null) {
                response.close();
            }
        }
        return jsonMap;
    }
}
