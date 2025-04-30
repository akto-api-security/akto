package com.akto.listener;


import static com.akto.listener.InitializerListener.createAndSaveAttackerRole;

import com.akto.action.HarAction;
import com.akto.analyser.ResourceAnalyser;
import com.akto.dao.AccountSettingsDao;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.SingleTypeInfoDao;
import com.akto.dao.context.Context;
import com.akto.dao.demo.VulnerableRequestForTemplateDao;
import com.akto.dto.Account;
import com.akto.dto.AccountSettings;
import com.akto.dto.ApiCollection;
import com.akto.dto.ApiInfo;
import com.akto.dto.demo.VulnerableRequestForTemplate;
import com.akto.dto.testing.AuthMechanism;
import com.akto.dto.testing.AuthParam;
import com.akto.dto.testing.HardcodedAuthParam;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.dto.type.URLMethods;
import com.akto.dto.type.URLMethods.Method;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.parsers.HttpCallParser;
import com.akto.runtime.Main;
import com.akto.runtime.policies.AktoPolicyNew;
import com.akto.util.AccountTask;
import com.akto.utils.AccountHTTPCallParserAktoPolicyInfo;
import com.akto.utils.Utils;
import com.google.gson.Gson;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.json.JSONArray;

public class RuntimeListener extends AfterMongoConnectListener {

    public static HttpCallParser httpCallParser = null;
    public static AktoPolicyNew aktoPolicyNew = null;
    public static ResourceAnalyser resourceAnalyser = null;
    //todo add resource analyser in AccountHttpCallParserAktoPolicyInfo
    public static final String JUICE_SHOP_DEMO_COLLECTION_NAME = "juice_shop_demo";
    public static final String VULNERABLE_API_COLLECTION_NAME = "vulnerable_apis";
    public static final String LLM_API_COLLECTION_NAME = "llm_apis";
    public static final int VULNERABLE_API_COLLECTION_ID = 1111111111;
    public static final int LLM_API_COLLECTION_ID = 1222222222;

    public static Map<Integer, AccountHTTPCallParserAktoPolicyInfo> accountHTTPParserMap = new ConcurrentHashMap<>();
    private static final LoggerMaker logger = new LoggerMaker(RuntimeListener.class, LogDb.DASHBOARD);

    @Override
    public void runMainFunction() {
        AccountTask.instance.executeTask(new Consumer<Account>() {
            @Override
            public void accept(Account account) {
                Main.initializeRuntimeHelper();
                if (account.getId() != 1_000_000) return;
                // only for 1M we want to run demo data
                AccountHTTPCallParserAktoPolicyInfo info = new AccountHTTPCallParserAktoPolicyInfo();
                HttpCallParser callParser = new HttpCallParser("userIdentifier", 1, 1, 1, false);
                info.setHttpCallParser(callParser);
//                info.setResourceAnalyser(new ResourceAnalyser(300_000, 0.01, 100_000, 0.01));
                accountHTTPParserMap.put(account.getId(), info);


                try {
                    initialiseDemoCollections();
                    //addSampleData();
                } catch (Exception e) {
                    logger.errorAndAddToDb(e,"Error while initialising demo collections: " + e, LoggerMaker.LogDb.DASHBOARD);
                }
            }
        }, "runtime-listner-task");
    }

    public static void initialiseDemoCollections() {
        AccountSettings accountSettings = AccountSettingsDao.instance.findOne(new BasicDBObject());
        if (accountSettings != null && accountSettings.getDemoCollectionCreateTime() > 0) {
            long count = VulnerableRequestForTemplateDao.instance.count(new BasicDBObject());
            if (count < 1) {
                //initialise vulnerable requests for templates in case its not present in db
                //insertVulnerableRequestsForDemo();
                logger.debugAndAddToDb("map created in db for vulnerable requests and corresponding templates", LoggerMaker.LogDb.DASHBOARD);
            }
            logger.debugAndAddToDb("Demo collections already initialised", LoggerMaker.LogDb.DASHBOARD);
            return;
        }

        // get har file from github
        String url = "https://raw.githubusercontent.com/akto-api-security/tests-library/master/resources/juiceshop.har";
        String harString = "";
        try {
            harString = new Scanner(new URL(url).openStream(), "UTF-8").useDelimiter("\\A").next();
        } catch (IOException e) {
            logger.errorAndAddToDb(e,"Error downlaoding from github: " + e, LoggerMaker.LogDb.DASHBOARD);
            return;
        }

        String tokensUrl = "https://raw.githubusercontent.com/akto-api-security/tests-library/master/resources/juiceshop_tokens.json";
        Map<String, String> tokens = new HashMap<>();
        try {
            String tokenJsonString = new Scanner(new URL(tokensUrl).openStream(), "UTF-8").useDelimiter("\\A").next();
            tokens = new Gson().fromJson(tokenJsonString, Map.class);
        } catch (IOException e) {
            logger.errorAndAddToDb(e,"Error downloading from github: " + e, LoggerMaker.LogDb.DASHBOARD);
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
            logger.errorAndAddToDb(e,"Error: " + e, LoggerMaker.LogDb.DASHBOARD);
        }

        // auth mechanism
        String attackerKey = tokens.get("attackerKey");
        String attackerToken  = tokens.get("attackerToken");
        List<AuthParam> authParamList = new ArrayList<>();
        authParamList.add(new HardcodedAuthParam(AuthParam.Location.HEADER, attackerKey, attackerToken, true));
        AuthMechanism authMechanism = new AuthMechanism(
             authParamList, new ArrayList<>(), "HARDCODED", null
        );
        ObjectId id = new ObjectId();
        authMechanism.setId(id);
        createAndSaveAttackerRole(authMechanism);

        //inserting first time during initialisation of demo collections
        insertVulnerableRequestsForDemo();

        AccountSettingsDao.instance.updateOne(
                AccountSettingsDao.generateFilter(),
                Updates.set(AccountSettings.DEMO_COLLECTION_CREATE_TIME, Context.now())
        );
    }

    public static void addSampleData() {
        List<String> result = new ArrayList<>();

        ApiCollection sameNameCollection = ApiCollectionsDao.instance.findByName(VULNERABLE_API_COLLECTION_NAME);
        if (sameNameCollection == null){
            ApiCollection apiCollection = new ApiCollection(VULNERABLE_API_COLLECTION_ID, VULNERABLE_API_COLLECTION_NAME, Context.now(),new HashSet<>(), null, VULNERABLE_API_COLLECTION_ID, false, true);
            ApiCollectionsDao.instance.insertOne(apiCollection);
        }

        try {
            String mockServiceUrl = "https://vulnerable-server.akto.io";
            String data = convertStreamToString(InitializerListener.class.getResourceAsStream("/SampleApiData.json"));
            JSONArray dataobject = new JSONArray(data);
            for (Object obj: dataobject) {

                Map<String, Object> json = new Gson().fromJson(obj.toString(), Map.class);
                Map<String, Object> sampleDataMap = (Map)json.get("sampleData");
                String testId = (String) json.get("id");

                int ts = Context.now();
                sampleDataMap.put("akto_account_id", ""+Context.accountId.get());
                sampleDataMap.put("ip", "null");
                sampleDataMap.put("time", String.valueOf(ts));
                sampleDataMap.put("type", "HTTP/1.1");
                sampleDataMap.put("contentType", "application/json");
                sampleDataMap.put("source", "HAR");
                sampleDataMap.put("akto_vxlan_id", VULNERABLE_API_COLLECTION_ID);

                String path = (String) sampleDataMap.get("path");
                sampleDataMap.put("path", mockServiceUrl + path);

                String jsonInString = new Gson().toJson(sampleDataMap);
                result.add(jsonInString);

                VulnerableRequestForTemplate vulnerableRequestForTemplate = new VulnerableRequestForTemplate();
                List<String> testList = new ArrayList<>();
                testList.add(testId);
                //vulnerableRequestForTemplate.setTemplateIds(testList);

                if (testId.equals("XSS_VIA_APPENDING_TO_QUERY_PARAMS")) {
                    logger.debug("hi");
                }

                String p = (String) sampleDataMap.get("path");
                String []split = p.split("\\?");

                if (split.length > 1) {
                    p = split[0];
                }

                ApiInfo.ApiInfoKey apiInfoKey = new ApiInfo.ApiInfoKey(
                    VULNERABLE_API_COLLECTION_ID, p, Method.fromString((String) sampleDataMap.get("method"))
                );
                // vulnerableRequestForTemplate.setId(apiInfoKey);

                VulnerableRequestForTemplate vul = VulnerableRequestForTemplateDao.instance.findOne(
                    Filters.in("templateIds", testList)
                );
                if (vul == null) {
                    VulnerableRequestForTemplateDao.instance.getMCollection().insertOne(new VulnerableRequestForTemplate(apiInfoKey, testList));
                }

                Map<String, Object> testDataMap = (Map)json.get("testData");
            }
            Bson filters = Filters.eq(SingleTypeInfo._API_COLLECTION_ID, VULNERABLE_API_COLLECTION_ID);
            List<SingleTypeInfo> params = SingleTypeInfoDao.instance.findAll(filters);
            Set<String> urlList = new HashSet<>();
            for (SingleTypeInfo singleTypeInfo: params) {
                urlList.add(singleTypeInfo.getUrl());
            }
            if (urlList.size() != 202) {
                Utils.pushDataToKafka(VULNERABLE_API_COLLECTION_ID, "", result, new ArrayList<>(), true, true);
            }

        } catch (Exception e) {
            // add log
            logger.errorAndAddToDb(e,"error inserting vulnerable app data" + e.getMessage(), LoggerMaker.LogDb.DASHBOARD);
        }

    }

    public static void addLlmSampleData(int accountId) {
        List<String> result = new ArrayList<>();

        logger.debugAndAddToDb("adding llm sample data for account" + accountId, LoggerMaker.LogDb.DASHBOARD);
        ApiCollection sameNameCollection = ApiCollectionsDao.instance.findByName(LLM_API_COLLECTION_NAME);
        if (sameNameCollection == null){
            ApiCollection apiCollection = new ApiCollection(LLM_API_COLLECTION_ID, LLM_API_COLLECTION_NAME, Context.now(),new HashSet<>(), null, LLM_API_COLLECTION_ID, false, true);
            ApiCollectionsDao.instance.insertOne(apiCollection);
        }

        try {
            String mockServiceUrl = "https://vuln-llm.akto.io";
            String data = convertStreamToString(InitializerListener.class.getResourceAsStream("/LlmSampleApiData.json"));
            JSONArray dataobject = new JSONArray(data);
            for (Object obj: dataobject) {

                try {
                    Map<String, Object> json = new Gson().fromJson(obj.toString(), Map.class);
                    Map<String, Object> sampleDataMap = (Map)json.get("sampleData");
                    String testId = (String) json.get("id");

                    int ts = Context.now();
                    sampleDataMap.put("akto_account_id", ""+accountId);
                    sampleDataMap.put("ip", "null");
                    sampleDataMap.put("time", String.valueOf(ts));
                    sampleDataMap.put("type", "HTTP/1.1");
                    sampleDataMap.put("contentType", "application/json");
                    sampleDataMap.put("source", "HAR");
                    sampleDataMap.put("akto_vxlan_id", LLM_API_COLLECTION_ID);

                    String path = (String) sampleDataMap.get("path");
                    sampleDataMap.put("path", mockServiceUrl + path);

                    String jsonInString = new Gson().toJson(sampleDataMap);
                    result.add(jsonInString);

                    VulnerableRequestForTemplate vulnerableRequestForTemplate = new VulnerableRequestForTemplate();
                    List<String> testList = new ArrayList<>();
                    testList.add(testId);
                    //vulnerableRequestForTemplate.setTemplateIds(testList);

                    String p = (String) sampleDataMap.get("path");
                    String []split = p.split("\\?");

                    if (split.length > 1) {
                        p = split[0];
                    }

                    ApiInfo.ApiInfoKey apiInfoKey = new ApiInfo.ApiInfoKey(
                        LLM_API_COLLECTION_ID, p, Method.fromString((String) sampleDataMap.get("method"))
                    );
                    // vulnerableRequestForTemplate.setId(apiInfoKey);



                    VulnerableRequestForTemplate vul = VulnerableRequestForTemplateDao.instance.findOne(
                        Filters.eq("_id.apiCollectionId", LLM_API_COLLECTION_ID)
                    );
                    if (vul == null) {
                        VulnerableRequestForTemplateDao.instance.getMCollection().insertOne(new VulnerableRequestForTemplate(apiInfoKey, testList));
                    } else {
                        List<String> templateIds = vul.getTemplateIds();
                        if (templateIds.contains(testId)) {
                            continue;
                        } else {
                            templateIds.add(testId);
                            Bson update = Updates.set("templateIds", templateIds);
                            VulnerableRequestForTemplateDao.instance.getMCollection().updateOne(
                                Filters.eq("_id.apiCollectionId", LLM_API_COLLECTION_ID), update, new UpdateOptions());
                        }
                    }
                } catch (Exception e) {
                    logger.errorAndAddToDb(e,"error inserting demo vul req", LoggerMaker.LogDb.DASHBOARD);
                }
                

            }
            logger.debugAndAddToDb("create vulnerable mapping" + accountId, LoggerMaker.LogDb.DASHBOARD);
            Utils.pushDataToKafka(LLM_API_COLLECTION_ID, "", result, new ArrayList<>(), true, true);

        } catch (Exception e) {
            // add log
            logger.errorAndAddToDb(e,"error inserting llm vulnerable app data" + e.getMessage(), LoggerMaker.LogDb.DASHBOARD);
        }

    }

    public static String convertStreamToString(InputStream in) throws Exception {

        BufferedReader reader = new BufferedReader(new InputStreamReader(in));
        StringBuilder stringbuilder = new StringBuilder();
        String line = null;
        while ((line = reader.readLine()) != null) {
            stringbuilder.append(line + "\n");
        }
        in.close();
        return stringbuilder.toString();
    }

    private static void insertVulnerableRequestsForDemo() {
        ApiCollection collection = ApiCollectionsDao.instance.findByName(JUICE_SHOP_DEMO_COLLECTION_NAME);
        if (collection == null) {
            logger.errorAndAddToDb("Error: collection not found", LoggerMaker.LogDb.DASHBOARD);
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
