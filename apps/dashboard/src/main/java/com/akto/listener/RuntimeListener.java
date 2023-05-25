package com.akto.listener;


import com.akto.action.HarAction;
import com.akto.dao.AccountSettingsDao;
import com.akto.dao.AuthMechanismsDao;
import com.akto.dao.context.Context;
import com.akto.dto.Account;
import com.akto.dto.AccountSettings;
import com.akto.dto.testing.AuthMechanism;
import com.akto.dto.testing.AuthParam;
import com.akto.dto.testing.HardcodedAuthParam;
import com.akto.log.LoggerMaker;
import com.akto.parsers.HttpCallParser;
import com.akto.runtime.Main;
import com.akto.runtime.policies.AktoPolicy;
import com.akto.util.AccountTask;
import com.akto.utils.AccountHTTPCallParserAktoPolicyInfo;
import com.google.gson.Gson;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Updates;

import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class RuntimeListener extends AfterMongoConnectListener {

    public static HttpCallParser httpCallParser = null;
    public static AktoPolicy aktoPolicy = null;

    public static Map<Integer, AccountHTTPCallParserAktoPolicyInfo> accountHTTPParserMap = new ConcurrentHashMap<>();

    private static final LoggerMaker loggerMaker= new LoggerMaker(RuntimeListener.class);
    @Override
    public void runMainFunction() {
        //todo create map and fill

        AccountTask.instance.executeTask(new Consumer<Account>() {
            @Override
            public void accept(Account account) {
                Main.initializeRuntime();
                AccountHTTPCallParserAktoPolicyInfo info = new AccountHTTPCallParserAktoPolicyInfo();
                HttpCallParser callParser = new HttpCallParser("userIdentifier", 1, 1, 1, false);
                info.setHttpCallParser(callParser);
                info.setPolicy(new AktoPolicy(callParser.apiCatalogSync, false));
                accountHTTPParserMap.put(account.getId(), info);

                try {
                    initialiseDemoCollections();
                } catch (Exception e) {
                    loggerMaker.errorAndAddToDb("Error while initialising demo collections: " + e, LoggerMaker.LogDb.DASHBOARD);
                }
            }
        }, "runtime-listner-task");
    }

    public static void initialiseDemoCollections() {
        AccountSettings accountSettings = AccountSettingsDao.instance.findOne(new BasicDBObject());
        if (accountSettings != null && accountSettings.getDemoCollectionCreateTime() > 0) {
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
        String attackerKey = tokens.get("attackerKey");
        String attackerToken  = tokens.get("attackerToken");
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
