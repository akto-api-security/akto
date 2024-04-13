package com.akto.action;

import com.akto.DaoInit;
import com.akto.action.test_editor.SaveTestEditorAction;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.BurpPluginInfoDao;
import com.akto.dao.SampleDataDao;
import com.akto.dao.context.Context;
import com.akto.dao.file.FilesDao;
import com.akto.dto.ApiCollection;
import com.akto.dto.ApiInfo;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.dependency_flow.DependencyFlow;
import com.akto.dto.testing.TestingRunResult;
import com.akto.dto.testing.YamlTestResult;
import com.akto.dto.traffic.SampleData;
import com.akto.dto.type.URLMethods;
import com.akto.har.HAR;
import com.akto.listener.InitializerListener;
import com.akto.listener.KafkaListener;
import com.akto.parsers.HttpCallParser;
import com.akto.runtime.APICatalogSync;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.ApiToken.Utility;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.har.HAR;
import com.akto.log.LoggerMaker;
import com.akto.dto.ApiToken.Utility;
import com.akto.test_editor.execution.Executor;
import com.akto.testing.yaml_tests.YamlTestTemplate;
import com.akto.util.DashboardMode;
import com.akto.utils.GzipUtils;
import com.akto.utils.Utils;
import com.mongodb.BasicDBObject;
import com.mongodb.ConnectionString;
import com.mongodb.client.model.Filters;
import com.opensymphony.xwork2.Action;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.sun.jna.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class HarAction extends UserAction {
    private String harString;
    private List<String> harErrors;
    private BasicDBObject content;
    private int apiCollectionId;
    private String apiCollectionName;

    private boolean skipKafka = DashboardMode.isLocalDeployment();
    private byte[] tcpContent;
    private static final LoggerMaker loggerMaker = new LoggerMaker(HarAction.class);

    public static void main(String[] args) throws IOException {
        DaoInit.init(new ConnectionString("mongodb://localhost:27017"));
        Context.accountId.set(1_000_000);

        DependencyFlow dependencyFlow = new DependencyFlow();
        dependencyFlow.run();
        dependencyFlow.syncWithDb();
    }

    public static void main3(String[] args) throws IOException {
        DaoInit.init(new ConnectionString("mongodb://localhost:27017"));
        Context.accountId.set(1_000_000);

        String filePath = "/Users/avneesh/Downloads/juiceshop_address_1.har";
        String content;
        try {
            content = new String(Files.readAllBytes(Paths.get(filePath)));
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        System.out.println(content.length());

        HarAction harAction = new HarAction();
        harAction.setHarString(content);
        harAction.setApiCollectionName("a3");
        harAction.skipKafka = true;
        harAction.execute();
    }

    public static void main2(String[] args) {
        DaoInit.init(new ConnectionString("mongodb://localhost:27017/admini"));
        Context.accountId.set(1_000_000);

        ApiInfo.ApiInfoKey apiInfoKey = new ApiInfo.ApiInfoKey(1712980524, "https://juiceshop.akto.io/api/Cards/INTEGER", URLMethods.Method.DELETE);

        SampleData sampleData = SampleDataDao.instance.findOne(
                Filters.and(
                        Filters.eq("_id.apiCollectionId", apiInfoKey.getApiCollectionId()),
                        Filters.eq("_id.method",apiInfoKey.getMethod().name()),
                        Filters.eq("_id.url",apiInfoKey.getUrl())
                )
        );

        BasicDBObject apiInfoKeyObj = new BasicDBObject()
                .append("apiCollectionId", apiInfoKey.getApiCollectionId())
                .append("url", apiInfoKey.getUrl())
                .append("method", apiInfoKey.getMethod().name());

        SaveTestEditorAction saveTestEditorAction = new SaveTestEditorAction();
        saveTestEditorAction.setApiInfoKey(apiInfoKeyObj);
        saveTestEditorAction.setContent(testContent);
        saveTestEditorAction.setSampleDataList(Collections.singletonList(sampleData));
        String result = saveTestEditorAction.runTestForGivenTemplate();
        System.out.println(result);

        TestingRunResult testingRunResult = saveTestEditorAction.getTestingRunResult();
        System.out.println(testingRunResult.getTestResults().size());
        for (TestingRunResult.TestLog testLog: testingRunResult.getTestLogs()) {
            System.out.println(testLog.getMessage());
        }
    }

    public static void main1(String[] args) {
        System.out.println(testContent);
    }


    public static String testContent = "id: REPORT_GENERATION_DOS\n" +
            "\n" +
            "info:\n" +
            "  name: \"Denial of Service Test on Report Generation Endpoint\"\n" +
            "  description: \"A Denial of Service (DoS) test\"\n" +
            "  details: \"In this test.\"\n" +
            "  impact: \"A\"\n" +
            "  category:\n" +
            "    name: RL\n" +
            "    shortName: Lack of Resources & Rate Limiting\n" +
            "    displayName: Lack of Resources & Rate Limiting (RL)\n" +
            "  subCategory: REPORT_GENERATION_DOS\n" +
            "  severity: HIGH\n" +
            "  tags:\n" +
            "    - Business logic\n" +
            "    - OWASP top 10\n" +
            "    - HackerOne top 10\n" +
            "  references:\n" +
            "    - \"https://github.com/OWASP/API-Security/blob/master/2019/en/src/0xa4-lack-of-resources-and-rate-limiting.md#scenario-2\"\n" +
            "  cwe:\n" +
            "    - CWE-400\n" +
            "  cve:\n" +
            "    - CVE-2023-4647\n" +
            "    - CVE-2023-38254\n" +
            "\n" +
            "\n" +
            "api_selection_filters:\n" +
            "  response_code:\n" +
            "    gte: 200\n" +
            "    lt: 300\n" +
            "\n" +
            "execute:\n" +
            "  type: graph\n" +
            "  requests:\n" +
            "  - req:\n" +
            "    - replace_auth_header: true\n" +
            "    - validate:\n" +
            "        percentage_match:\n" +
            "          gte: 90\n" +
            "    - success: x2\n" +
            "    - failure: x2\n" +
            "  - req:\n" +
            "    - api: get_asset_api\n" +
            "    - validate: \n" +
            "       response_code: 4xx\n" +
            "       success: vulnerability\n" +
            "       failure: exit\n" +
            "\n" +
            "\n" +
            "\n" +
            "validate:\n" +
            "  and:\n" +
            "    - compare_greater:\n" +
            "        - ${x2.response.stats.median_response_time}\n" +
            "        - 3001\n" +
            "    - compare_greater:\n" +
            "        - ${x2.response.stats.median_response_time}\n" +
            "        - ${x1.response.stats.median_response_time} * 3";

    public String executeWithSkipKafka(boolean skipKafka) throws IOException {
        this.skipKafka = skipKafka;
        execute();
        return SUCCESS.toUpperCase();
    }

    @Override
    public String execute() throws IOException {
        if (InitializerListener.isKubernetes()) {
            skipKafka = true;
        }

        ApiCollection apiCollection = null;
        loggerMaker.infoAndAddToDb("HarAction.execute() started", LoggerMaker.LogDb.DASHBOARD);
        if (apiCollectionName != null) {
            apiCollection =  ApiCollectionsDao.instance.findByName(apiCollectionName);
            if (apiCollection == null) {
                ApiCollectionsAction apiCollectionsAction = new ApiCollectionsAction();
//                apiCollectionsAction.setSession(this.getSession());
                apiCollectionsAction.setCollectionName(apiCollectionName);
                String result = apiCollectionsAction.createCollection();
                if (result.equalsIgnoreCase(Action.SUCCESS)) {
                    List<ApiCollection> apiCollections = apiCollectionsAction.getApiCollections();
                    if (apiCollections != null && apiCollections.size() > 0) {
                        apiCollection = apiCollections.get(0);
                    } else {
                        addActionError("Couldn't create api collection " +  apiCollectionName);
                        return ERROR.toUpperCase();
                    }
                } else {
                    Collection<String> actionErrors = apiCollectionsAction.getActionErrors(); 
                    if (actionErrors != null && actionErrors.size() > 0) {
                        for (String actionError: actionErrors) {
                            addActionError(actionError);
                        }
                    }
                    return ERROR.toUpperCase();
                }
            }

            apiCollectionId = apiCollection.getId();
        } else {
            apiCollection =  ApiCollectionsDao.instance.findOne(Filters.eq("_id", apiCollectionId));
        }

        if (apiCollection == null) {
            addActionError("Invalid collection name");
            return ERROR.toUpperCase();
        }

        if (apiCollection.getHostName() != null)  {
            addActionError("Traffic mirroring collection can't be used");
            return ERROR.toUpperCase();
        }

        if (!skipKafka && KafkaListener.kafka == null) {
            addActionError("Dashboard kafka not running");
            return ERROR.toUpperCase();
        }

        if (ApiCollection.Type.API_GROUP.equals(apiCollection.getType()))  {
            addActionError("API groups can't be used");
            return ERROR.toUpperCase();
        }

        if (harString == null) {
            harString = this.content.toString();
        }
        String topic = System.getenv("AKTO_KAFKA_TOPIC_NAME");
        if (topic == null) topic = "akto.api.logs";
        if (harString == null) {
            addActionError("Empty content");
            return ERROR.toUpperCase();
        }

//        if (getSession().getOrDefault("utility","").equals(Utility.BURP.toString())) {
//            BurpPluginInfoDao.instance.updateLastDataSentTimestamp(getSUser().getLogin());
//        }

        try {
            HAR har = new HAR();
            loggerMaker.infoAndAddToDb("Har file upload processing for collectionId:" + apiCollectionId, LoggerMaker.LogDb.DASHBOARD);
//            String zippedString = GzipUtils.zipString(harString);
//            com.akto.dto.files.File file = new com.akto.dto.files.File(HttpResponseParams.Source.HAR.toString(),zippedString);
//            FilesDao.instance.insertOne(file);
            List<String> messages = har.getMessages(harString, apiCollectionId, Context.accountId.get());
            harErrors = har.getErrors();
            Utils.pushDataToKafka(apiCollectionId, topic, messages, harErrors, skipKafka);
            loggerMaker.infoAndAddToDb("Har file upload processing for collectionId:" + apiCollectionId + " finished", LoggerMaker.LogDb.DASHBOARD);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e,"Exception while parsing harString", LoggerMaker.LogDb.DASHBOARD);
            e.printStackTrace();
            return SUCCESS.toUpperCase();
        }
        return SUCCESS.toUpperCase();
    }

    public void setContent(BasicDBObject content) {
        this.content = content;
    }

    public void setApiCollectionId(int apiCollectionId) {
        this.apiCollectionId = apiCollectionId;
    }

    public void setHarString(String harString) {
        this.harString = harString;
    }

    public void setApiCollectionName(String apiCollectionName) {
        this.apiCollectionName = apiCollectionName;
    }

    public List<String> getHarErrors() {
        return harErrors;
    }

    public boolean getSkipKafka() {
        return this.skipKafka;
    }

    public void setTcpContent(byte[] tcpContent) {
        this.tcpContent = tcpContent;
    }

    Awesome awesome = null;

    public String uploadTcp() {
        
        File tmpDir = FileUtils.getTempDirectory();
        String filename = UUID.randomUUID().toString() + ".pcap";
        File tcpDump = new File(tmpDir, filename);
        try {
            FileUtils.writeByteArrayToFile(tcpDump, tcpContent);
            Awesome awesome =  (Awesome) Native.load("awesome", Awesome.class);
            Awesome.GoString.ByValue str = new Awesome.GoString.ByValue();
            str.p = tcpDump.getAbsolutePath();
            str.n = str.p.length();
    
            Awesome.GoString.ByValue str2 = new Awesome.GoString.ByValue();
            str2.p = System.getenv("AKTO_KAFKA_BROKER_URL");
            str2.n = str2.p.length();
    
            awesome.readTcpDumpFile(str, str2 , apiCollectionId);
    
            return Action.SUCCESS.toUpperCase();            
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            return Action.ERROR.toUpperCase();        
        }

    }

    interface Awesome extends Library {          
        public static class GoString extends Structure {
            /** C type : const char* */
            public String p;
            public long n;
            public GoString() {
                super();
            }
            protected List<String> getFieldOrder() {
                return Arrays.asList("p", "n");
            }
            /** @param p C type : const char* */
            public GoString(String p, long n) {
                super();
                this.p = p;
                this.n = n;
            }
            public static class ByReference extends GoString implements Structure.ByReference {}
            public static class ByValue extends GoString implements Structure.ByValue {}
        }
        
        public void readTcpDumpFile(GoString.ByValue filepath, GoString.ByValue kafkaURL, long apiCollectionId);
        
    }
}
