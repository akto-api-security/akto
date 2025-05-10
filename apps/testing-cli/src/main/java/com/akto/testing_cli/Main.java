package com.akto.testing_cli;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.bson.BsonReader;
import org.bson.Document;
import org.bson.codecs.Codec;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.akto.DaoInit;
import com.akto.dao.context.Context;
import com.akto.dao.test_editor.TestConfigYamlParser;
import com.akto.dao.test_editor.TestEditorEnums.ContextOperator;
import com.akto.dto.AccountSettings;
import com.akto.dto.ApiCollection;
import com.akto.dto.ApiInfo;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.CustomAuthType;
import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.OriginalHttpResponse;
import com.akto.dto.RawApi;
import com.akto.dto.test_editor.TestConfig;
import com.akto.dto.testing.AuthMechanism;
import com.akto.dto.testing.TestingRunConfig;
import com.akto.dto.testing.TestingRunResult;
import com.akto.dto.traffic.SampleData;
import com.akto.store.SampleMessageStore;
import com.akto.store.TestingUtil;
import com.akto.testing.ApiExecutor;
import com.akto.testing.TestExecutor;
import com.akto.testing.Utils;
import com.akto.util.ColorConstants;
import com.akto.util.VersionUtil;

public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static final String AKTO_DASHBOARD_URL = System.getenv("AKTO_DASHBOARD_URL");
    public static final String AKTO_API_KEY = System.getenv("AKTO_API_KEY");
    public static final String ALL_STRING = "all";

    private static String callDashboardApi(String path, String body) {

        Map<String, List<String>> headers = new HashMap<>();
        headers.put("Content-Type", Collections.singletonList("application/json"));
        headers.put("X-API-KEY", Collections.singletonList(AKTO_API_KEY));
        String url = AKTO_DASHBOARD_URL;
        if (url.endsWith("/")) {
            url += path;
        } else {
            url += "/" + path;
        }
        OriginalHttpRequest request = new OriginalHttpRequest(url, "", "POST", body, headers, "");
        OriginalHttpResponse response = null;

        try {
            response = ApiExecutor.sendRequest(request, true, null, false, new ArrayList<>());
        } catch (Exception e) {
            logger.error("api execution failed");
            return "";
        }

        return response.getBody();

    }

    public static <T> T decode(Codec<T> codec, Document doc){
        BsonReader bsonReader = doc.toBsonDocument().asBsonReader();
        return codec.decode(bsonReader, DecoderContext.builder().build());
    }

    public enum OUTPUT_LEVEL {
        NONE, SUMMARY, DETAILED, DEBUG
    }

    static String getSeverity(Map<String, TestConfig> testConfigMap, TestingRunResult it) {
        String severity = "HIGH";
        try {
            severity = testConfigMap.get(it.getTestSubType()).getInfo().getSeverity();
        } catch (Exception e) {
            severity = "HIGH";
        }
        return severity;
    }

    public static void main(String[] args) {

        if (AKTO_DASHBOARD_URL == null || AKTO_DASHBOARD_URL.isEmpty() || AKTO_API_KEY == null
                || AKTO_API_KEY.isEmpty()) {
            logger.error("Please set AKTO_DASHBOARD_URL and AKTO_API_KEY");
            return;
        }

        String body = "";
        String res = "";
        Document doc;

        CodecRegistry codecRegistry = DaoInit.createCodecRegistry();

        res = callDashboardApi("api/fetchAdminSettings", body);
        doc = Document.parse(res);
        Document adminSettings = ((Document) doc.get("accountSettings"));
        Codec<AccountSettings> accountSettingsCodec = codecRegistry.get(AccountSettings.class);
        AccountSettings accountSettings = decode(accountSettingsCodec, adminSettings);

        String version = "";
        try (InputStream in = Main.class.getResourceAsStream("/version.txt")) {
            if (in != null) {
                version = VersionUtil.getVersion(in);
            } else {
                throw new Exception("Input stream null");
            }
        } catch (Exception e) {
            logger.error("Error getting local version");
        }

        String accountVersion = accountSettings.getDashboardVersion();
        if (version == null || accountVersion == null) {
            String versionError = ColorConstants.RED + "Unable to find testing cli tool version."
                    + ColorConstants.RESET;
            logger.info(versionError);
        } else if (!version.equals(accountVersion)) {
            String versionError = ColorConstants.RED + "Please update your testing cli tool [ " + version
                    + " ] to the latest version [ " + accountVersion + " ] on akto dashboard\n" +
                    ColorConstants.GREEN + "You can do a docker pull to update the testing cli tool."
                    + ColorConstants.RESET;
            logger.info(versionError);
        }

        res = callDashboardApi("api/fetchAllSubCategories", "{'skip': 0, 'limit': 2000}");
        doc = Document.parse(res);
        List<Document> subCategoriesList = doc.getList("subCategories", Document.class, new ArrayList<>());

        Map<String, TestConfig> testConfigMap = new HashMap<>();
        for (Document it : subCategoriesList) {
            String subCategory = it.getString("name");
            String content = it.getString("content");
            try {
                testConfigMap.put(subCategory, TestConfigYamlParser.parseTemplate(content));
                testConfigMap.get(subCategory).setContent(content);
            } catch (Exception e) {
                logger.error("Error parsing test config for subcategory: %s", subCategory);
            }
        }

        String testIds = System.getenv("TEST_IDS");
        List<String> testIdsList = new ArrayList<>();
        if (testIds != null && !testIds.isEmpty()) {

            if(testIds.equalsIgnoreCase(ALL_STRING)){
                testIdsList =  new ArrayList<>(testConfigMap.keySet()); 
            } else {
                String[] tmp = testIds.split(" ");
                testIdsList = Arrays.asList(tmp);
            }

        } else {
            logger.error("No test ids to test");
            return;
        }

        testIdsList = testIdsList.stream().filter(
            obj -> {
                if(!testConfigMap.containsKey(obj)){
                    String error = "Unknown test. Skipping " + obj;
                    logger.error(error);
                    return false;
                }
                String content = testConfigMap.get(obj).getContent();
                if(content.contains(ContextOperator.ENDPOINT_IN_TRAFFIC_CONTEXT.toString().toLowerCase()) 
                    || content.contains(ContextOperator.PARAM_CONTEXT.toString().toLowerCase())
                    || content.contains(ContextOperator.PRIVATE_VARIABLE_CONTEXT.toString().toLowerCase())
                    || content.contains(ContextOperator.INCLUDE_ROLES_ACCESS.toString().toLowerCase())
                    || content.contains(ContextOperator.EXCLUDE_ROLES_ACCESS.toString().toLowerCase())
                    || content.contains(ContextOperator.API_ACCESS_TYPE.toString().toLowerCase())){
                        String info = "Cannot run context tests. Skipping " + obj;
                        logger.info(info);
                        return false;
                }
                return true;
            }).collect(Collectors.toList());

        List<ApiInfoKey> apiInfoKeys = new ArrayList<>();

        String apiCollectionName = System.getenv("API_COLLECTION_NAME");

        Map<Integer, ApiCollection> apiCollectionMap = new HashMap<>();
        ApiCollection apiCollection = new ApiCollection();
        apiCollection.setId(-1);

        try {
            res = callDashboardApi("api/getAllCollections", body);
            doc = Document.parse(res);
            List<Document> docList = doc.getList("apiCollections", Document.class, new ArrayList<>());
            for(Document apiCollectionDoc: docList){
                apiCollectionDoc.put("_id", apiCollectionDoc.get("id"));
                Codec<ApiCollection> apiCollectionCodec = codecRegistry.get(ApiCollection.class);
                ApiCollection temp = decode(apiCollectionCodec, apiCollectionDoc);
                apiCollectionMap.put(temp.getId(), temp);
                if(temp.getDisplayName().equals(apiCollectionName)){
                    apiCollection = temp;
                }
            }
        } catch (Exception e) {
            logger.error("Could not fetch api collection list");
        }

        String apiCollectionId = "";
        if(apiCollection.getId()!=-1){
            apiCollectionId = String.valueOf(apiCollection.getId());
        } else {
            apiCollectionId = System.getenv("API_COLLECTION_ID");
            apiCollection = apiCollectionMap.getOrDefault(Integer.parseInt(apiCollectionId), new ApiCollection());
        }
        

        if (apiCollectionId != null && !apiCollectionId.isEmpty()) {
            body = "{\"apiCollectionId\": \"" + apiCollectionId + "\"}";
            res = callDashboardApi("api/fetchCollectionWiseApiEndpoints", body);
            List<ApiInfoKey> keys = new ArrayList<>();
            doc = Document.parse(res);
            List<Document> tmp = doc.getList("listOfEndpointsInCollection", Document.class, new ArrayList<>());
            Codec<ApiInfoKey> apiInfoKeyCodec = codecRegistry.get(ApiInfoKey.class);
            for (Document it : tmp) {
                ApiInfoKey key = decode(apiInfoKeyCodec, it);
                keys.add(key);
            }
            apiInfoKeys = keys;
        } else {
            logger.error("No api collection to test");
            return;
        }

        String testApis = System.getenv("TEST_APIS");
        if (testApis != null && !testApis.isEmpty()) {
            String[] tmp = testApis.split(" ");
            List<String> testApisList = Arrays.asList(tmp);
            apiInfoKeys = apiInfoKeys.stream().filter(
                    obj -> testApisList.contains(obj.getUrl())).collect(Collectors.toList());
        }

        res = callDashboardApi("api/fetchAuthMechanismDataDoc", "");

        Codec<AuthMechanism> authMechCodec = codecRegistry.get(AuthMechanism.class);
        doc = Document.parse(res);
        Document authMech = ((Document) doc.get("authMechanismDoc"));
        ObjectId idd = new ObjectId((String) authMech.get("objectId"));
        authMech.put("_id", idd);
        AuthMechanism authMechanism = decode(authMechCodec, authMech);

        boolean dataLeft = true;
        int skip = 0;
        int limit = 50;
        Map<ApiInfo.ApiInfoKey, List<String>> sampleDataMap = new HashMap<>();
        do {
            body = "{\"apiCollectionId\": \"" + apiCollectionId + "\" , \"skip\": \"" + skip + "\" }";
            res = callDashboardApi("api/fetchAllSampleData", body);
            doc = Document.parse(res);
            List<Document> tmp = doc.getList("sampleDataList", Document.class, new ArrayList<>());
            Codec<SampleData> sampleDataCodec = codecRegistry.get(SampleData.class);
            for (Document object : tmp) {
                Document id = ((Document) object.get("id"));
                object.put("_id", id);
                SampleData sampleData = decode(sampleDataCodec, object);
                sampleDataMap.put(new ApiInfoKey(sampleData.getId().getApiCollectionId(), sampleData.getId().getUrl(),
                        sampleData.getId().getMethod()), sampleData.getSamples());
            }
            if (tmp==null || tmp.size() < limit) {
                dataLeft = false;
            }
            skip += 50;

        } while (dataLeft);

        SampleMessageStore messageStore = SampleMessageStore.create(sampleDataMap);

        res = callDashboardApi("api/fetchCustomAuthTypes", "");
        doc = Document.parse(res);
        Codec<CustomAuthType> customAuthTypeCodec = codecRegistry.get(CustomAuthType.class);
        List<Document> customAuthTypesList = doc.getList("customAuthTypes", Document.class, new ArrayList<>());
        List<CustomAuthType> customAuthTypes = new ArrayList<>();
        for (Document it : customAuthTypesList) {
            ObjectId id = new ObjectId((String) it.get("hexId"));
            it.put("_id", id);
            CustomAuthType customAuthType = decode(customAuthTypeCodec, it);
            if(customAuthType.isActive()){
                customAuthTypes.add(customAuthType);
            }
        }

        // role set to null  is going to throw an exception
        TestingUtil testingUtil = new TestingUtil(messageStore, null, null, customAuthTypes);

        List<TestingRunResult> testingRunResults = new ArrayList<>();
        TestExecutor testExecutor = new TestExecutor();

        String overrideAppUrl = System.getenv("OVERRIDE_APP_URL");

        TestingRunConfig testingRunConfig = new TestingRunConfig();
        if (overrideAppUrl != null && !overrideAppUrl.isEmpty()) {
            testingRunConfig.setOverriddenTestAppUrl(overrideAppUrl);
        }
        testingRunConfig.setTestSubCategoryList(testIdsList);

        Map<String, Integer> severityMap = new HashMap<>();
        int totalVulnerabilities = 0;
        for (String testSubCategory : testingRunConfig.getTestSubCategoryList()) {
            TestConfig testConfig = testConfigMap.get(testSubCategory);
            for (ApiInfo.ApiInfoKey it : apiInfoKeys) {
                TestingRunResult testingRunResult = null;
                try {
                    List<String> samples = testingUtil.getSampleMessages().get(it);
                    testingRunResult = Utils.generateFailedRunResultForMessage(null, it, testConfig.getInfo().getCategory().getName(), testConfig.getInfo().getSubCategory(), null,samples , null);
                    if(testingRunResult == null){
                        String sample = samples.get(samples.size() - 1);
                        testingRunResult = testExecutor.runTestNew(it, null, testingUtil, null, testConfig, null, false, new ArrayList<>(), sample);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                if (testingRunResult != null){
                    testingRunResults.add(testingRunResult);
                    String severity = testConfig.getInfo().getSeverity();
                    if(testingRunResult.isVulnerable()){
                        severityMap.put(severity, severityMap.getOrDefault(severity, 0) + 1);
                        totalVulnerabilities++;
                    }
                }
            }
        }

        logger.info("\n");
        testingRunResults.sort(
                (a, b) -> {
                    return a.compareTo(b);
                });

        logger.info(ColorConstants.YELLOW + "API collection: " + apiCollectionId + " "
                + apiCollection.getDisplayName() + "\n" + ColorConstants.RESET);

        logger.info("Tested " + apiInfoKeys.size() + " API" + (apiInfoKeys.size()==1 ? "" : "s") + "\n");

        logger.info(ColorConstants.RED + "Total vulnerabilities: " + totalVulnerabilities + "\n" + ColorConstants.RESET);
        if(totalVulnerabilities>0){
            for (Map.Entry<String, Integer> entry : severityMap.entrySet()) {
                logger.info(ColorConstants.RED + entry.getKey() + ": " + entry.getValue() + "\n" + ColorConstants.RESET);
            }
        }

        Map<String, List<ApiInfo.ApiInfoKey>> vulnerableTestToApiMap = new HashMap<>();

        for (TestingRunResult it : testingRunResults) {
            String severity = getSeverity(testConfigMap, it);

            if(it.isVulnerable()){
                List<ApiInfo.ApiInfoKey> tmp = vulnerableTestToApiMap.getOrDefault(it.getTestSubType(), new ArrayList<>());
                tmp.add(it.getApiInfoKey());
                vulnerableTestToApiMap.put(it.getTestSubType(), tmp);
            }
            String output = it.toConsoleString(severity);
            logger.info(output);
        }

        OUTPUT_LEVEL outputLevel = OUTPUT_LEVEL.SUMMARY;

        try {
            outputLevel = OUTPUT_LEVEL.valueOf(System.getenv("OUTPUT_LEVEL"));
        } catch (Exception e){
            logger.info("Using default output level: SUMMARY");
        }

        if(outputLevel.equals(OUTPUT_LEVEL.NONE)){
            System.exit(0);
        }

        String fileDir = "../out/";
        String filePath = fileDir + "output.txt";
        
        try {
            boolean dirCreated = new File(fileDir).mkdirs();
            if(dirCreated){
                String message = "output directory created at " + fileDir;
                logger.info(message);
            }
        } catch (Exception e){
            logger.error(e.getMessage());
        }

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(new File(filePath)))) {
            writer.write("Api collection: " + apiCollectionId + " " + apiCollection.getDisplayName() + "\n\n");

            if (totalVulnerabilities > 0) {
                writer.write("Vulnerabilities: \n");
                for (Map.Entry<String, Integer> entry : severityMap.entrySet()) {
                    writer.write(entry.getKey() + ": " + entry.getValue() + "\n");
                }
                writer.write("\n");

                for (Map.Entry<String, List<ApiInfo.ApiInfoKey>> entry : vulnerableTestToApiMap.entrySet()) {
                    TestConfig testConfig = testConfigMap.getOrDefault(entry.getKey(), null);
                    
                    writer.write("Test ID: " + entry.getKey() + "\n");

                    if(testConfig != null){
                        writer.write("Test name: " + testConfig.getInfo().getName() + "\n");
                        writer.write("Severity: " + testConfig.getInfo().getSeverity() + "\n");

                        if(!outputLevel.equals(OUTPUT_LEVEL.SUMMARY)){
                            writer.write("Description: " + testConfig.getInfo().getDescription() + "\n");
                            writer.write("Impact: " + testConfig.getInfo().getImpact() + "\n\n");
                        }

                    }

                    writer.write("APIs affected: \n");
                    for(ApiInfo.ApiInfoKey apiInfoKey: entry.getValue()){
                        writer.write(apiInfoKey.getUrl() + " " + apiInfoKey.getMethod().toString() + "\n");
                    }
                    writer.write("\n ********************* \n\n");
                }
            } else {
                writer.write("No vulnerabilities found \n\n");
            }

            if(outputLevel.equals(OUTPUT_LEVEL.DEBUG)){
                writer.write("DEBUG result: \n");
                for (TestingRunResult it : testingRunResults) {
                    String severity = getSeverity(testConfigMap, it);
                    String output = it.toOutputString(severity) + "\n ------------------------------------ \n\n";
                    writer.write(output);
                }
            }
            logger.info("Detailed result is written to output.txt");
        } catch (Exception e) {
            String error = "Error writing to file " + filePath + " due to " + e.getMessage();
            logger.error(error);
        }

        System.exit(0);

    }
}
