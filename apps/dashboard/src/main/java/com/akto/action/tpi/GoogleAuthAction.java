package com.akto.action.tpi;

import com.akto.action.UserAction;
import com.akto.dao.ConfigsDao;
import com.akto.dao.ThirdPartyAccessDao;
import com.akto.dao.context.Context;
import com.akto.dto.Config;
import com.akto.dto.third_party_access.GoogleCredential;
import com.akto.dto.third_party_access.ThirdPartyAccess;
import com.akto.listener.InitializerListener;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeTokenRequest;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.opensymphony.xwork2.Action;
import java.io.IOException;

public class GoogleAuthAction extends UserAction {

    private static final LoggerMaker logger = new LoggerMaker(GoogleAuthAction.class, LogDb.DASHBOARD);;

    private BasicDBObject googleConfigResult;
    public String retrieveGoogleConfig() {
        Config.GoogleConfig googleConfig =(Config.GoogleConfig) ConfigsDao.instance.findOne(
                Filters.eq("configType", Config.ConfigType.GOOGLE.name())
        );
        if (googleConfig== null) return Action.ERROR.toUpperCase();

        googleConfigResult = new BasicDBObject();
        googleConfigResult.put("client_id",googleConfig.getClientId());
        return Action.SUCCESS.toUpperCase();
    }

    private String code;
    private String accessToken;
    private int thirdPartyId;

    public String sendGoogleAuthCodeToServer() {
        try {
            Config.GoogleConfig googleConfig =(Config.GoogleConfig) ConfigsDao.instance.findOne("_id", "GOOGLE-ankush");

            GoogleTokenResponse tokenResponse =
                new GoogleAuthorizationCodeTokenRequest(
                    new NetHttpTransport(),
                    JacksonFactory.getDefaultInstance(),
                    "https://oauth2.googleapis.com/token",
                    googleConfig.getClientId(),
                    googleConfig.getClientSecret(),
                    code,
                        InitializerListener.getDomain())
                    .execute();

            this.accessToken = tokenResponse.getAccessToken();

            String refreshToken = tokenResponse.getRefreshToken();

            int id = Context.getId();

            GoogleIdToken.Payload payload = tokenResponse.parseIdToken().getPayload();
            String userId = payload.get("name") + " - " + payload.getEmail();

            ThirdPartyAccessDao.instance.insertOne(
                new ThirdPartyAccess(
                        Context.now(),
                        getSUser().getId(),
                        0,
                        new GoogleCredential(accessToken, refreshToken, tokenResponse.getExpiresInSeconds(), userId+"")
                )
            );

            this.thirdPartyId = id;

            this.code = userId;

        } catch (IOException e) {
            logger.error(e.getMessage());
            e.printStackTrace();
            return Action.ERROR.toUpperCase();
        }

        return Action.SUCCESS.toUpperCase();
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public int getThirdPartyId() {
        return thirdPartyId;
    }

    public void setThirdPartyId(int thirdPartyId) {
        this.thirdPartyId = thirdPartyId;
    }

    public BasicDBObject getGoogleConfigResult() {
        return googleConfigResult;
    }
}
