package com.akto.listener;


import com.akto.action.HarAction;
import com.akto.dao.AccountSettingsDao;
import com.akto.dao.context.Context;
import com.akto.dto.AccountSettings;
import com.akto.log.LoggerMaker;
import com.akto.parsers.HttpCallParser;
import com.akto.runtime.Main;
import com.akto.runtime.policies.AktoPolicy;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Updates;

import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

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
        String url = "https://raw.githubusercontent.com/avneesh-akto/tests-library/temp/har_file/petstore-patch.har";
        String harString = "";
        try {
            harString = new Scanner(new URL(url).openStream(), "UTF-8").useDelimiter("\\A").next();
        } catch (IOException e) {
            loggerMaker.errorAndAddToDb("Error downlaoding from github: " + e, LoggerMaker.LogDb.DASHBOARD);
            return;
        }

        // process har file
        HarAction harAction = new HarAction();
        harAction.setHarString(harString);
        harAction.setApiCollectionName("petstore_demo");
        Map<String, Object> session = new HashMap<>();
        harAction.setSession(session);
        // todo: skipKafka = true for onPrem also
        try {
            harAction.execute();
        } catch (IOException e) {
            loggerMaker.errorAndAddToDb("Error: " + e, LoggerMaker.LogDb.DASHBOARD);
        }

        // auth mechanism

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
