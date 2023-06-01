package com.akto.listener;


import com.akto.analyser.ResourceAnalyser;
import com.akto.action.HarAction;
import com.akto.dao.AccountSettingsDao;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.AuthMechanismsDao;
import com.akto.dao.context.Context;
import com.akto.dao.demo.VulnerableRequestForTemplateDao;
import com.akto.dto.AccountSettings;
import com.akto.dto.ApiCollection;
import com.akto.dto.ApiInfo;
import com.akto.dto.demo.VulnerableRequestForTemplate;
import com.akto.dto.testing.AuthMechanism;
import com.akto.dto.testing.AuthParam;
import com.akto.dto.testing.HardcodedAuthParam;
import com.akto.dto.type.URLMethods;
import com.akto.log.LoggerMaker;
import com.akto.parsers.HttpCallParser;
import com.akto.runtime.Main;
import com.akto.runtime.policies.AktoPolicyNew;
import com.google.gson.Gson;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Updates;

import java.io.IOException;
import java.net.URL;
import java.util.*;

public class RuntimeListener extends AfterMongoConnectListener {

    public static HttpCallParser httpCallParser = null;
    public static AktoPolicyNew aktoPolicy = null;
    public static ResourceAnalyser resourceAnalyser = null;
    public static final String JUICE_SHOP_DEMO_COLLECTION_NAME = "juice_shop_demo";

    private final LoggerMaker loggerMaker= new LoggerMaker(RuntimeListener.class);

    @Override
    public void runMainFunction() {
        Context.accountId.set(1_000_000);
        Main.initializeRuntime();
        httpCallParser = new HttpCallParser("userIdentifier", 1, 1, 1, false);
        aktoPolicy = new AktoPolicyNew(false);
        resourceAnalyser = new ResourceAnalyser(300_000, 0.01, 100_000, 0.01);

        try {
            initialiseDemoCollections();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error while initialising demo collections: " + e, LoggerMaker.LogDb.DASHBOARD);
        }
    }

    public void initialiseDemoCollections() {
        AccountSettings accountSettings = AccountSettingsDao.instance.findOne(new BasicDBObject());
        if (accountSettings != null && accountSettings.getDemoCollectionCreateTime() > 0) {
            long count = VulnerableRequestForTemplateDao.instance.count(new BasicDBObject());
            if (count < 1) {
                //initialise vulnerable requests for templates in case its not present in db
                insertVulnerableRequestsForDemo();
                loggerMaker.infoAndAddToDb("map created in db for vulnerable requests and corresponding templates", LoggerMaker.LogDb.DASHBOARD);
            }
            loggerMaker.infoAndAddToDb("Demo collections already initialised", LoggerMaker.LogDb.DASHBOARD);
            return;
        }

        // get har file from github
        String url = "https://raw.githubusercontent.com/akto-api-security/tests-library/master/resources/juiceshop.har";
        String harString = "";
        try {
            harString = new Scanner(new URL(url).openStream(), "UTF-8").useDelimiter("\\A").next();
        } catch (IOException e) {
            loggerMaker.errorAndAddToDb("Error downlaoding from github: " + e, LoggerMaker.LogDb.DASHBOARD);
            return;
        }

        String tokensUrl = "https://raw.githubusercontent.com/akto-api-security/tests-library/master/resources/juiceshop_tokens.json";
        Map<String, String> tokens = new HashMap<>();
        try {
            String tokenJsonString = new Scanner(new URL(tokensUrl).openStream(), "UTF-8").useDelimiter("\\A").next();
            tokens = new Gson().fromJson(tokenJsonString, Map.class);
        } catch (IOException e) {
            loggerMaker.errorAndAddToDb("Error downloading from github: " + e, LoggerMaker.LogDb.DASHBOARD);
            return;
        }

        String victimToken = tokens.get("victimToken");
        harString = harString.replaceAll("\\{\\{AKTO\\.token\\}\\}", victimToken);

        // process har file
        HarAction harAction = new HarAction();
        harAction.setHarString(harString);
        harAction.setApiCollectionName(JUICE_SHOP_DEMO_COLLECTION_NAME);
        Map<String, Object> session = new HashMap<>();
        harAction.setSession(session);
        // todo: skipKafka = true for onPrem also
        try {
            harAction.executeWithSkipKafka(true);
        } catch (IOException e) {
            loggerMaker.errorAndAddToDb("Error: " + e, LoggerMaker.LogDb.DASHBOARD);
        }

        // auth mechanism
        String attackerKey = tokens.get("attackerKey");
        String attackerToken  = tokens.get("attackerToken");
        List<AuthParam> authParamList = new ArrayList<>();
        authParamList.add(new HardcodedAuthParam(AuthParam.Location.HEADER, attackerKey, attackerToken, true));
        AuthMechanism authMechanism = new AuthMechanism(
             authParamList, new ArrayList<>(), "HARDCODED"
        );
        AuthMechanismsDao.instance.insertOne(authMechanism);

        //inserting first time during initialisation of demo collections
        insertVulnerableRequestsForDemo();

        AccountSettingsDao.instance.updateOne(
                AccountSettingsDao.generateFilter(),
                Updates.set(AccountSettings.DEMO_COLLECTION_CREATE_TIME, Context.now())
        );
    }

    private void insertVulnerableRequestsForDemo() {
        ApiCollection collection = ApiCollectionsDao.instance.findByName(JUICE_SHOP_DEMO_COLLECTION_NAME);
        if (collection == null) {
            loggerMaker.errorAndAddToDb("Error: collection not found", LoggerMaker.LogDb.DASHBOARD);
            return;
        }
        Map<String, List<String>> apiVsTemplateMap = VulnerableRequestForTemplateDao.getApiVsTemplateMap();
        List<VulnerableRequestForTemplate> vulnerableRequestForTemplates = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : apiVsTemplateMap.entrySet()) {
            String apiName = entry.getKey();
            List<String> templates = entry.getValue();
            String[] apiNameParts = apiName.split(" ");
            String method = apiNameParts[0];
            String path = apiNameParts[1];
            ApiInfo.ApiInfoKey apiInfoKey = new ApiInfo.ApiInfoKey(collection.getId(), path, URLMethods.Method.fromString(method));
            vulnerableRequestForTemplates.add(new VulnerableRequestForTemplate(apiInfoKey, templates));
        }
        VulnerableRequestForTemplateDao.instance.insertMany(vulnerableRequestForTemplates);
    }

    //

    @Override
    public int retryAfter() {
        return 60;
    }

}
