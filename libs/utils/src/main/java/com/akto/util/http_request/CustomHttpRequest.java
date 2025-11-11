package com.akto.util.http_request;

import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
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

    private static void logRequestDetails(Request request) {
        try {
            loggerMaker.infoAndAddToDb("=== HTTP REQUEST DETAILS ===");
            loggerMaker.infoAndAddToDb("Request URL: " + request.url().toString());
            loggerMaker.infoAndAddToDb("Request Method: " + request.method());

            // Log headers
            StringBuilder headersLog = new StringBuilder("Request Headers: {");
            request.headers().toMultimap().forEach((key, values) -> {
                headersLog.append(key).append(": ").append(String.join(", ", values)).append(", ");
            });
            if (headersLog.length() > 25) {
                headersLog.setLength(headersLog.length() - 2); // Remove trailing comma and space
            }
            headersLog.append("}");
            loggerMaker.infoAndAddToDb(headersLog.toString());

            // Log request body if available
            if (request.body() != null) {
                try {
                    okio.Buffer buffer = new okio.Buffer();
                    request.body().writeTo(buffer);
                    String bodyString = buffer.readUtf8();
                    loggerMaker.infoAndAddToDb("Request Body: " + bodyString);
                } catch (Exception e) {
                    loggerMaker.infoAndAddToDb("Request Body: (unable to read body - " + e.getMessage() + ")");
                }
            } else {
                loggerMaker.infoAndAddToDb("Request Body: (empty)");
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error logging request details: " + e.getMessage());
        }
    }

    private static void logResponseDetails(Response response, String responseBody) {
        try {
            loggerMaker.infoAndAddToDb("=== HTTP RESPONSE DETAILS ===");
            loggerMaker.infoAndAddToDb("Response Status Code: " + response.code());
            loggerMaker.infoAndAddToDb("Response Message: " + response.message());

            // Log headers
            StringBuilder headersLog = new StringBuilder("Response Headers: {");
            response.headers().toMultimap().forEach((key, values) -> {
                headersLog.append(key).append(": ").append(String.join(", ", values)).append(", ");
            });
            if (headersLog.length() > 27) {
                headersLog.setLength(headersLog.length() - 2); // Remove trailing comma and space
            }
            headersLog.append("}");
            loggerMaker.infoAndAddToDb(headersLog.toString());

            // Log response body
            if (responseBody != null && !responseBody.isEmpty()) {
                loggerMaker.infoAndAddToDb("Response Body: " + responseBody);
            } else {
                loggerMaker.infoAndAddToDb("Response Body: (empty)");
            }
            loggerMaker.infoAndAddToDb("=== END HTTP RESPONSE ===");
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error logging response details: " + e.getMessage());
        }
    }

    public static Map<String, Object> s(Request request) throws HttpResponseException {
        // Log request details before executing
        logRequestDetails(request);

        //Execute and get the response.
        Response response = null;
        Map<String, Object> jsonMap = new HashMap<>();
        String jsonData = null;
        try {
            response = httpClient.newCall(request).execute();
            if (response == null) {
                loggerMaker.infoAndAddToDb("response null");
                return null;
            }

            jsonData = response.body().string();

            // Log response details
            logResponseDetails(response, jsonData);

            if (!response.isSuccessful()) {
                throw new HttpResponseException(response.code(), response.message());
            }

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
