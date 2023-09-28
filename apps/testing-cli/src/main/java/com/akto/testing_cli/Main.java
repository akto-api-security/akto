package com.akto.testing_cli;

import com.akto.DaoInit;
import com.akto.dao.test_editor.TestConfigYamlParser;
import com.akto.dto.*;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.test_editor.TestConfig;
import com.akto.dto.testing.AuthMechanism;
import com.akto.dto.testing.TestingRunConfig;
import com.akto.dto.testing.TestingRunResult;
import com.akto.dto.traffic.SampleData;
import com.akto.store.SampleMessageStore;
import com.akto.store.TestingUtil;
import com.akto.testing.ApiExecutor;
import com.akto.testing.TestExecutor;
import com.akto.util.ColorConstants;
import com.akto.util.VersionUtil;
import org.bson.BsonReader;
import org.bson.Document;
import org.bson.codecs.Codec;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static final String AKTO_DASHBOARD_URL = System.getenv("AKTO_DASHBOARD_URL");
    public static final String AKTO_API_KEY = System.getenv("AKTO_API_KEY");

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
            response = ApiExecutor.sendRequest(request, true, null);
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
            System.out.println(versionError);
        } else if (!version.equals(accountVersion)) {
            String versionError = ColorConstants.RED + "Please update your testing cli tool [ " + version
                    + " ] to the latest version [ " + accountVersion + " ] on akto dashboard\n" +
                    ColorConstants.GREEN + "You can do a docker pull to update the testing cli tool."
                    + ColorConstants.RESET;
            System.out.println(versionError);
        }

        res = callDashboardApi("api/fetchAllSubCategories", body);
        doc = Document.parse(res);
        List<Document> subCategoriesList = doc.getList("subCategories", Document.class, new ArrayList<>());

        Map<String, TestConfig> testConfigMap = new HashMap<>();
        for (Document it : subCategoriesList) {
            String subCategory = it.getString("name");
            String content = it.getString("content");
            try {
                testConfigMap.put(subCategory, TestConfigYamlParser.parseTemplate(content));
            } catch (Exception e) {
                logger.error("Error parsing test config for subcategory: %s", subCategory);
            }
        }

        String testIds = System.getenv("TEST_IDS");
        List<String> testIdsList = new ArrayList<>();
        if (testIds != null && !testIds.isEmpty()) {
            String[] tmp = testIds.split(" ");
            testIdsList = Arrays.asList(tmp);
        } else {
            logger.error("No test ids to test");
            return;
        }

        List<ApiInfoKey> apiInfoKeys = new ArrayList<>();

        String apiCollectionId = System.getenv("API_COLLECTION_ID");

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

        ApiCollection apiCollection = new ApiCollection();
        apiCollection.setName("");
        apiCollection.setId(Integer.parseInt(apiCollectionId));

        try {
            res = callDashboardApi("api/getCollection", body);
            doc = Document.parse(res);
            List<Document> docList = doc.getList("apiCollections", Document.class, new ArrayList<>());
            Document apiCollectionDoc = docList.get(0);
            apiCollectionDoc.put("_id", apiCollectionDoc.get("id"));
            Codec<ApiCollection> apiCollectionCodec = codecRegistry.get(ApiCollection.class);
            apiCollection = decode(apiCollectionCodec, apiCollectionDoc);
        } catch (Exception e) {
            logger.error("Could not find api collection");
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

        TestingUtil testingUtil = new TestingUtil(authMechanism, messageStore, null, null, new ArrayList<>());

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
                    testingRunResult = testExecutor.runTestNew(it, null, testingUtil, null, testConfig,
                            testingRunConfig);
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

        System.out.println("\n");
        testingRunResults.sort(
                (a, b) -> {
                    if (a.isVulnerable()) {
                        return -1;
                    }
                    return 1;
                });

        System.out.println(ColorConstants.YELLOW + "API collection: " + apiCollectionId + " "
                + apiCollection.getDisplayName() + "\n" + ColorConstants.RESET);
        
        System.out.println(ColorConstants.RED + "Total vulnerabilities: " + totalVulnerabilities + "\n" + ColorConstants.RESET);
        if(totalVulnerabilities>0){
            for (Map.Entry<String, Integer> entry : severityMap.entrySet()) {
            System.out.println(ColorConstants.RED + entry.getKey() + ": " + entry.getValue() + "\n" + ColorConstants.RESET);
            }
        }

        for (TestingRunResult it : testingRunResults) {
            String severity = testConfigMap.get(it.getTestSubType()).getInfo().getSeverity();
            String output = it.toConsoleString(severity);
            System.out.println(output);
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
            for (TestingRunResult it : testingRunResults) {
                String output = it.toOutputString() + "\n ------------------------------------ \n\n";
                writer.write(output);
            }
            System.out.println("Detailed result is written to output.txt");
        } catch (Exception e) {
            String error = "Error writing to file " + filePath + " due to " + e.getMessage();
            logger.error(error);
        }
    }
}
