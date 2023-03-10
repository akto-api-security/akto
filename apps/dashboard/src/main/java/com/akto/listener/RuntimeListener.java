package com.akto.listener;


import com.akto.action.HarAction;
import com.akto.dao.AccountSettingsDao;
import com.akto.dao.AuthMechanismsDao;
import com.akto.dao.context.Context;
import com.akto.dto.AccountSettings;
import com.akto.dto.testing.AuthMechanism;
import com.akto.dto.testing.AuthParam;
import com.akto.dto.testing.HardcodedAuthParam;
import com.akto.log.LoggerMaker;
import com.akto.parsers.HttpCallParser;
import com.akto.runtime.Main;
import com.akto.runtime.policies.AktoPolicy;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Updates;

import java.io.IOException;
import java.net.URL;
import java.util.*;

public class RuntimeListener extends AfterMongoConnectListener {

    public static HttpCallParser httpCallParser = null;
    public static AktoPolicy aktoPolicy = null;

    private final LoggerMaker loggerMaker= new LoggerMaker(RuntimeListener.class);

    @Override
    public void runMainFunction() {
        Context.accountId.set(1_000_000);
        Main.initializeRuntime();
        httpCallParser = new HttpCallParser("userIdentifier", 1, 1, 1, false);
        aktoPolicy = new AktoPolicy(RuntimeListener.httpCallParser.apiCatalogSync, false);

        try {
            initialiseDemoCollections();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error while initialising demo collections: " + e, LoggerMaker.LogDb.DASHBOARD);
        }
    }

    public void initialiseDemoCollections() {
        AccountSettings accountSettings = AccountSettingsDao.instance.findOne(new BasicDBObject());
        if (accountSettings != null && accountSettings.getDemoCollectionCreateTime() > 0) {
            return;
        }

        // get har file from github
        String url = "https://raw.githubusercontent.com/avneesh-akto/tests-library/temp/har_file/juice_onboarding_latest.har";
        String harString = "";
        try {
            harString = new Scanner(new URL(url).openStream(), "UTF-8").useDelimiter("\\A").next();
        } catch (IOException e) {
            loggerMaker.errorAndAddToDb("Error downlaoding from github: " + e, LoggerMaker.LogDb.DASHBOARD);
            return;
        }

        String victimToken = "Bearer eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiJ9.eyJzdGF0dXMiOiJzdWNjZXNzIiwiZGF0YSI6eyJpZCI6MjEsInVzZXJuYW1lIjoidmljdGltIiwiZW1haWwiOiJ2aWN0aW1AZ21haWwuY29tIiwicGFzc3dvcmQiOiJhNjJlN2JlMGE1NjQwODFiNmE5Zjc1MzA4MjA4YzQzMyIsInJvbGUiOiJjdXN0b21lciIsImRlbHV4ZVRva2VuIjoiIiwibGFzdExvZ2luSXAiOiIiLCJwcm9maWxlSW1hZ2UiOiJhc3NldHMvcHVibGljL2ltYWdlcy91cGxvYWRzL2RlZmF1bHQuc3ZnIiwidG90cFNlY3JldCI6IiIsImlzQWN0aXZlIjp0cnVlLCJjcmVhdGVkQXQiOiIyMDIzLTAzLTEwIDA1OjM5OjE4LjI5OSArMDA6MDAiLCJ1cGRhdGVkQXQiOiIyMDIzLTAzLTEwIDA1OjM5OjE4LjI5OSArMDA6MDAiLCJkZWxldGVkQXQiOm51bGx9LCJpYXQiOjE2Nzg0MjY4NjUsImV4cCI6MTk5Mzc4Njg2NX0.bUvn24at2rOcuht5hto8QHl7pXdanuLKQDBxqH2MWG2-mMEI8LgWm1R9HhUD209dHL93Ks52KijKJFOlF_5Z3-v47jY-Rf73wcA_Le69-n7EudWwrc_X6EGpNiqovVYm31RZQnU2Q_H-PtzpnzNIOnfE6z_p023acrke-cZkKss";
        harString = harString.replaceAll("\\{\\{AKTO\\.token\\}\\}", victimToken);

        // process har file
        HarAction harAction = new HarAction();
        harAction.setHarString(harString);
        harAction.setApiCollectionName("juice_shop_demo");
        Map<String, Object> session = new HashMap<>();
        harAction.setSession(session);
        // todo: skipKafka = true for onPrem also
        try {
            harAction.execute();
        } catch (IOException e) {
            loggerMaker.errorAndAddToDb("Error: " + e, LoggerMaker.LogDb.DASHBOARD);
        }

        // auth mechanism
        String attackerKey = "Authorization";
        String attackerToken = "Bearer eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiJ9.eyJzdGF0dXMiOiJzdWNjZXNzIiwiZGF0YSI6eyJpZCI6MjIsInVzZXJuYW1lIjoiYXR0YWNrZXIiLCJlbWFpbCI6ImF0dGFja2VyQGdtYWlsLmNvbSIsInBhc3N3b3JkIjoiMTY2YzU1YjUxZmQ1ZmJkYTg3NWFiNmMzMTg0NDIyNTUiLCJyb2xlIjoiY3VzdG9tZXIiLCJkZWx1eGVUb2tlbiI6IiIsImxhc3RMb2dpbklwIjoiIiwicHJvZmlsZUltYWdlIjoiYXNzZXRzL3B1YmxpYy9pbWFnZXMvdXBsb2Fkcy9kZWZhdWx0LnN2ZyIsInRvdHBTZWNyZXQiOiIiLCJpc0FjdGl2ZSI6dHJ1ZSwiY3JlYXRlZEF0IjoiMjAyMy0wMy0xMCAwNTozOToxOC4yOTkgKzAwOjAwIiwidXBkYXRlZEF0IjoiMjAyMy0wMy0xMCAwNTozOToxOC4yOTkgKzAwOjAwIiwiZGVsZXRlZEF0IjpudWxsfSwiaWF0IjoxNjc4NDI3Mjg1LCJleHAiOjE5OTM3ODcyODV9.hBAPAJm1FZIpDb7fm4nT3GY_u3R0KeyjqK-Ns5pcz22RN5_qhWt-K98y8DdELjUsRKVodAFPOki0QBmAqdhp5umgJB1ZPk4uEKLg2AI6ztr5729UezMbQozbIOu8UFmVm2crJn5YZKCbPKCcDwRUpisICbjDtJ5PD41RhZfLut8";
        List<AuthParam> authParamList = new ArrayList<>();
        authParamList.add(new HardcodedAuthParam(AuthParam.Location.HEADER, attackerKey, attackerToken, true));
        AuthMechanism authMechanism = new AuthMechanism(
             authParamList, new ArrayList<>(), "HARDCODED"
        );
        AuthMechanismsDao.instance.insertOne(authMechanism);

        AccountSettingsDao.instance.updateOne(
                AccountSettingsDao.generateFilter(),
                Updates.set(AccountSettings.DEMO_COLLECTION_CREATE_TIME, Context.now())
        );
    }

    @Override
    public int retryAfter() {
        return 60;
    }

}
