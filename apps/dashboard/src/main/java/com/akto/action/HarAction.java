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
import com.akto.dto.testing.TestingRunResult;
import com.akto.dto.testing.YamlTestResult;
import com.akto.dto.traffic.SampleData;
import com.akto.dto.type.URLMethods;
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
import com.sun.jna.*;

import java.io.File;
import java.io.IOException;
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

    public static void main(String[] args) {
        DaoInit.init(new ConnectionString("mongodb://localhost:27017/admini"));
        Context.accountId.set(1_000_000);

        ApiInfo.ApiInfoKey apiInfoKey = new ApiInfo.ApiInfoKey(1710156663, "https://juiceshop.akto.io/rest/products/reviews", URLMethods.Method.PATCH);

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
            "info:\n" +
            "  name: \"Denial of Service Test on Report Generation Endpoint\"\n" +
            "  description: \"A Denial of Service (DoS) test on a report generation endpoint involves \n" +
            "  overwhelming the system with a high volume of requests to assess its \n" +
            "  resilience under heavy load. By bombarding the endpoint with \n" +
            "  numerous simultaneous requests for report generation, testers \n" +
            "  evaluate how well the system handles the load and whether it \n" +
            "  remains responsive. This testing helps identify potential bottlenecks \n" +
            "  or vulnerabilities in the report generation process, enabling \n" +
            "  proactive measures to fortify the system's defenses against \n" +
            "  DoS attacks targeting this endpoint.\"\n" +
            "  details: \"In this test, the report generation endpoint is bombarded with an \n" +
            "  excessive number of requests, aiming to simulate real-world peak \n" +
            "  loads and stress the system. Testers assess how the endpoint \n" +
            "  responds to this influx of requests, evaluating its ability \n" +
            "  to maintain responsiveness and generate reports efficiently. \n" +
            "  Through this process, potential weaknesses in scalability \n" +
            "  and performance are identified, enabling organizations to \n" +
            "  fortify their systems against Denial of Service (DoS) attacks \n" +
            "  on report generation functionalities.\"\n" +
            "  impact: \"A successful Denial of Service (DoS) test on a report generation endpoint \n" +
            "  can have significant consequences. It may lead to system slowdowns, \n" +
            "  unavailability, or crashes, hindering users' access to vital reports \n" +
            "  and disrupting business operations. Additionally, prolonged \n" +
            "  service disruptions can tarnish the organization's reputation, \n" +
            "  eroding user trust and potentially resulting in financial \n" +
            "  losses. Identifying and addressing vulnerabilities in the \n" +
            "  report generation process is crucial for maintaining system \n" +
            "  reliability and resilience against DoS attacks.\"\n" +
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
            "api_selection_filters:\n" +
            "  response_code:\n" +
            "    gte: 200\n" +
            "    lt: 300\n" +
            "  url:\n" +
            "    contains_either:\n" +
            "      - a\n" +
            "wordLists:\n" +
            "  dummyHeaders:\n" +
            "    - a\n" +
            "\n" +
            "execute:\n" +
            "  type: multiple\n" +
            "  requests:\n" +
            "  - req:\n" +
            "    - api: AVNEESH\n" +
            "    - add_header:\n" +
            "        dummy_Header_Key: \"dummyValue\"\n" +
            "    - validate:\n" +
            "        percentage_match:\n" +
            "          gte: 90\n" +
            "    - success: x2\n" +
            "    - failure: exit\n" +
            "\n" +
            "  - req:\n" +
            "    - api: AVNEESH\n" +
            "    - add_header:\n" +
            "        dummy_Header_Key: \"dummyValue\"\n" +
            "    - validate:\n" +
            "        percentage_match:\n" +
            "          gte: 90\n" +
            "    - success: x3\n" +
            "    - failure: exit\n" +
            "\n" +
            "  - req:\n" +
            "    - api: AVNEESH\n" +
            "    - add_header:\n" +
            "        dummy_Header_Key: \"dummyValue\"\n" +
            "    - validate:\n" +
            "        percentage_match:\n" +
            "          gte: 90\n" +
            "    - success: x4\n" +
            "    - failure: exit\n" +
            "\n" +
            "  - req:\n" +
            "    - api: AVNEESH\n" +
            "    - add_header:\n" +
            "        dummy_Header_Key: \"dummyValue\"\n" +
            "    - validate:\n" +
            "        percentage_match:\n" +
            "          gte: 90\n" +
            "    - success: x5\n" +
            "    - failure: exit\n" +
            "\n" +
            "validate:\n" +
            "  and:\n" +
            "    - compare_greater:\n" +
            "        - ${x2.response.stats.median_response_time}\n" +
            "        - 3001\n" +
            "    - compare_greater:\n" +
            "        - ${x2.response.stats.median_response_time}\n" +
            "        - ${x1.response.stats.median_response_time} * 3";
    @Override
    public String execute() throws IOException {
        ApiCollection apiCollection = null;
        loggerMaker.infoAndAddToDb("HarAction.execute() started", LoggerMaker.LogDb.DASHBOARD);
        if (apiCollectionName != null) {
            apiCollection =  ApiCollectionsDao.instance.findByName(apiCollectionName);
            if (apiCollection == null) {
                ApiCollectionsAction apiCollectionsAction = new ApiCollectionsAction();
                apiCollectionsAction.setSession(this.getSession());
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

        if (getSession().getOrDefault("utility","").equals(Utility.BURP.toString())) {
            BurpPluginInfoDao.instance.updateLastDataSentTimestamp(getSUser().getLogin());
        }

        try {
            HAR har = new HAR();
            loggerMaker.infoAndAddToDb("Har file upload processing for collectionId:" + apiCollectionId, LoggerMaker.LogDb.DASHBOARD);
            String zippedString = GzipUtils.zipString(harString);
            com.akto.dto.files.File file = new com.akto.dto.files.File(HttpResponseParams.Source.HAR.toString(),zippedString);
            FilesDao.instance.insertOne(file);
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
            ;
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