package com.akto.utils;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.akto.dao.WizIntegrationDao;
import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.OriginalHttpResponse;
import com.akto.dto.wiz_integration.WizIntegration;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.testing.ApiExecutor;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Updates;

import org.bson.conversions.Bson;

public class WizIntegrationUtils {

    private static final LoggerMaker loggerMaker = new LoggerMaker(WizIntegrationUtils.class, LogDb.DASHBOARD);
    public static final String AUTH_ENDPOINT = "https://auth.app.wiz.io/oauth/token";

    public static String generateAccessToken(String clientId, String clientSecret) throws Exception {
        if (clientId == null || clientSecret == null) {
            throw new Exception("Client ID and Client Secret are required");
        }

        loggerMaker.infoAndAddToDb("Generating Wiz access token");

        String formBody = "grant_type=client_credentials" +
                          "&audience=wiz-api" +
                          "&client_id=" + URLEncoder.encode(clientId, "UTF-8") +
                          "&client_secret=" + URLEncoder.encode(clientSecret, "UTF-8");

        Map<String, List<String>> headers = new HashMap<>();
        headers.put("Content-Type", Collections.singletonList("application/x-www-form-urlencoded"));

        OriginalHttpRequest request = new OriginalHttpRequest(AUTH_ENDPOINT, "", "POST", formBody, headers, "");
        OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, new ArrayList<>());

        if (response == null) {
            throw new Exception("Failed to get OAuth token from Wiz - null response");
        }

        String responsePayload = response.getBody();
        int statusCode = response.getStatusCode();

        if (statusCode != 200 || responsePayload == null) {
            String errorMsg = String.format("OAuth token request failed with status code: %d", statusCode);
            if (statusCode == 400) {
                errorMsg += " (access_denied - Unauthorized)";
            }
            throw new Exception(errorMsg);
        }

        BasicDBObject responseObj = BasicDBObject.parse(responsePayload);

        if (!responseObj.containsField("access_token")) {
            throw new Exception("No access_token in OAuth response");
        }

        String accessToken = responseObj.getString("access_token");
        int expiresIn = responseObj.getInt("expires_in", 86400);

        if (accessToken == null || accessToken.isEmpty()) { 
            throw new Exception("Received empty access token from Wiz"); 
        }

        loggerMaker.infoAndAddToDb(String.format("Successfully generated Wiz access token. Expires in: %d seconds", expiresIn));

        long tokenExpiryTs = System.currentTimeMillis() + (expiresIn * 1000L);

        Bson tokenUpdate = Updates.combine(
            Updates.set(WizIntegration.ACCESS_TOKEN,accessToken),
            Updates.set(WizIntegration.TOKEN_EXPIRY_TS, tokenExpiryTs)
        );

        WizIntegrationDao.instance.getMCollection().updateOne(
            new BasicDBObject(),
            tokenUpdate
        );

        return accessToken;
    }

    public static String getValidAccessToken() throws Exception {
        WizIntegration wizIntegration = WizIntegrationDao.instance.findOne(new BasicDBObject());

        if (wizIntegration == null) {
            throw new Exception("WizIntegration cannot be null");
        }

        if (wizIntegration.isTokenValid()) {
            loggerMaker.infoAndAddToDb("Using cached Wiz access token");
            return wizIntegration.getAccessToken();
        }

        loggerMaker.infoAndAddToDb("Cached access token expired or missing, generating new Wiz access token");

        String clientId = wizIntegration.getClientId(); 
        String clientSecret = wizIntegration.getClientSecret();
        return generateAccessToken(clientId, clientSecret);
    }
}
