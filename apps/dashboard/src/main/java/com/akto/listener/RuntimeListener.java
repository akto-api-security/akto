package com.akto.listener;


import com.akto.ApiRequest;
import com.akto.TimeoutObject;
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
import com.akto.dto.type.URLMethods.Method;
import com.akto.log.LoggerMaker;
import com.akto.parsers.HttpCallParser;
import com.akto.runtime.Main;
import com.akto.runtime.policies.AktoPolicyNew;
import com.akto.utils.Utils;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.gson.Gson;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.util.*;

import org.json.JSONArray;
import org.json.JSONObject;

public class RuntimeListener extends AfterMongoConnectListener {

    public static HttpCallParser httpCallParser = null;
    public static AktoPolicyNew aktoPolicyNew = null;
    public static final String JUICE_SHOP_DEMO_COLLECTION_NAME = "juice_shop_demo";
    public static final String VULNERABLE_API_COLLECTION_NAME = "vulnerable_apis";
    public static final int VULNERABLE_API_COLLECTION_ID = 1111111111;

    private final LoggerMaker loggerMaker= new LoggerMaker(RuntimeListener.class);

    @Override
    public void runMainFunction() {
        Context.accountId.set(1_000_000);
        Main.initializeRuntime();
        httpCallParser = new HttpCallParser("userIdentifier", 1, 1, 1, false);
        aktoPolicyNew = new AktoPolicyNew(false);

        try {
            initialiseDemoCollections();
            addSampleData();
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
            ApiCollection apiCollection = new ApiCollection(VULNERABLE_API_COLLECTION_ID, VULNERABLE_API_COLLECTION_NAME, Context.now(),new HashSet<>(), null, VULNERABLE_API_COLLECTION_ID);
            ApiCollectionsDao.instance.insertOne(apiCollection);
        }

        try {
            String mockServiceUrl = "http://127.0.0.1:8000"; //System.getenv("VULNERABLE_MOCKSERVER_SERVICE_URL");
            String data = convertStreamToString(InitializerListener.class.getResourceAsStream("/SampleApiData.json"));
            JSONArray dataobject = new JSONArray(data);
            for (Object obj: dataobject) {

                Map<String, Object> json = new Gson().fromJson(obj.toString(), Map.class);
                Map<String, Object> sampleDataMap = (Map)json.get("sampleData");
                String testId = (String) json.get("id");

                int ts = Context.now();
                sampleDataMap.put("akto_account_id", "1000000");
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
                vulnerableRequestForTemplate.setTemplateIds(testList);
                ApiInfo.ApiInfoKey apiInfoKey = new ApiInfo.ApiInfoKey(
                    VULNERABLE_API_COLLECTION_ID, (String) sampleDataMap.get("path"), Method.fromString((String) sampleDataMap.get("method"))
                );
                vulnerableRequestForTemplate.setId(apiInfoKey);

                UpdateOptions updateOptions = new UpdateOptions();
                updateOptions.upsert(true);

                VulnerableRequestForTemplate vul = VulnerableRequestForTemplateDao.instance.findOne(
                    Filters.in("templateIds", testList)
                );
                if (vul == null) {
                    VulnerableRequestForTemplateDao.instance.getMCollection().updateOne(
                        Filters.in("templateIds", testId),
                        Updates.combine(
                                Updates.set("_id", apiInfoKey),
                                Updates.set("templateIds", testList)
                        ),
                        updateOptions
                    );
                }

                Map<String, Object> testDataMap = (Map)json.get("testData");
                addDataToMockserver(testDataMap, mockServiceUrl);
            }
            Utils.pushDataToKafka(VULNERABLE_API_COLLECTION_ID, "", result, new ArrayList<>(), true);

        } catch (Exception e) {
            // add log
            System.out.println("error");
        }

        System.out.println("hi");

    }

    private static void addDataToMockserver(Map<String, Object> testDataMap, String mockServiceUrl) {
        String url = (String) testDataMap.get("url");
        // URI uri = null;
        // try {
        //     uri = new URI(path);
        // } catch (Exception e) {
        // }
        // String url = uri.getPath();
        JSONObject requestBody = new JSONObject();
        requestBody.put("url", url);

        JSONObject data = new JSONObject();
        data.put("method", testDataMap.get("method"));
        data.put("responsePayload", testDataMap.get("responsePayload"));
        data.put("statusCode", testDataMap.get("statusCode"));
        data.put("responseHeaders", testDataMap.get("responseHeaders"));

        requestBody.put("data", data);
        String reqData = requestBody.toString();

        TimeoutObject timeoutObj = new TimeoutObject(300, 300, 300);
        JsonNode node = null;
        try {
            node = ApiRequest.postRequestWithTimeout(new HashMap<>(), mockServiceUrl + "/api/add_sample_data/", reqData, timeoutObj);            
        } catch (Exception e) {
            // TODO: handle exception
        }
    }

    private static String convertStreamToString(InputStream in) throws Exception {

        BufferedReader reader = new BufferedReader(new InputStreamReader(in));
        StringBuilder stringbuilder = new StringBuilder();
        String line = null;
        while ((line = reader.readLine()) != null) {
            stringbuilder.append(line + "\n");
        }
        in.close();
        return stringbuilder.toString();
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
