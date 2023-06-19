package com.akto.action.growth_tools;

import com.akto.action.TrafficAction;
import com.akto.action.test_editor.SaveTestEditorAction;
import com.akto.action.testing_issues.IssuesAction;
import com.akto.dao.context.Context;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.demo.VulnerableRequestForTemplate;
import com.akto.dto.test_run_findings.TestingRunIssues;
import com.akto.dto.testing.TestingRunResult;
import com.akto.dto.testing.sources.TestSourceConfig;
import com.akto.dto.traffic.Key;
import com.akto.dto.traffic.SampleData;
import com.akto.dto.type.URLMethods;
import com.akto.util.enums.GlobalEnums;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.mongodb.BasicDBObject;
import com.opensymphony.xwork2.Action;
import com.opensymphony.xwork2.ActionSupport;
import org.apache.struts2.interceptor.ServletRequestAware;
import org.apache.struts2.interceptor.ServletResponseAware;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.*;

public class PublicApiAction extends ActionSupport implements Action, ServletResponseAware, ServletRequestAware {
    protected HttpServletRequest request;
    protected HttpServletResponse response;
    private ArrayList<BasicDBObject> subCategories;
    private GlobalEnums.TestCategory[] categories;
    private List<TestSourceConfig> testSourceConfigs;
    private List<VulnerableRequestForTemplate> vulnerableRequests;
    private List<SampleData> sampleDataList;
    private int apiCollectionId;
    private String url;
    private String method;

    private static ObjectMapper mapper = new ObjectMapper();
    private static Gson gson = new Gson();
    private String content;
    private BasicDBObject apiInfoKey;
    private TestingRunResult testingRunResult;
    private TestingRunIssues testingRunIssues;
    private Map<String, BasicDBObject> subCategoryMap;

    private String sampleRequestString;
    private String sampleResponseString;

    public static final String PATH = "path";
    public static final String TYPE = "type";
    public static final String METHOD = "method";
    public static final String REQUEST_PAYLOAD = "requestPayload";
    public static final String REQUEST_HEADERS = "requestHeaders";
    public static final String RESPONSE_PAYLOAD = "responsePayload";
    public static final String RESPONSE_HEADERS = "responseHeaders";
    public static final String AKTO_VXLAN_ID = "akto_vxlan_id";
    public static final String STATUS = "status";
    public static final String STATUS_CODE = "statusCode";

    @Override
    public String execute() throws Exception {
        return SUCCESS.toUpperCase();
    }

    public String runTestForGivenTemplate() {
        Context.accountId.set(1_000_000);
        SaveTestEditorAction testingAction = new SaveTestEditorAction();
        testingAction.setContent(content);
        testingAction.setSampleDataList(sampleDataList);
        testingAction.setApiInfoKey(apiInfoKey);
        String runResult = testingAction.runTestForGivenTemplate();
        if(runResult == ERROR.toUpperCase()) {
            for (String error : testingAction.getActionErrors()) {
                addActionError(error);
            }
            return ERROR.toUpperCase();
        }
        testingRunResult = testingAction.getTestingRunResult();
        testingRunIssues = testingAction.getTestingRunIssues();
        subCategoryMap = testingAction.getSubCategoryMap();
        return SUCCESS.toUpperCase();
    }
    public String fetchAllSubCategories() {
        Context.accountId.set(1_000_000);
        IssuesAction issuesAction = new IssuesAction();
        String runResult = issuesAction.fetchAllSubCategories();
        if(runResult == ERROR.toUpperCase()) {
            for (String error : issuesAction.getActionErrors()) {
                addActionError(error);
            }
            return ERROR.toUpperCase();
        }

        subCategories = issuesAction.getSubCategories();
        testSourceConfigs = issuesAction.getTestSourceConfigs();
        vulnerableRequests = issuesAction.getVulnerableRequests();
        categories = GlobalEnums.TestCategory.values();
        return SUCCESS.toUpperCase();
    }

    public String fetchSampleData() {
        Context.accountId.set(1_000_000);
        TrafficAction trafficAction = new TrafficAction();
        trafficAction.setApiCollectionId(apiCollectionId);
        trafficAction.setUrl(url);
        trafficAction.setMethod(method);
        trafficAction.fetchSampleData();

        sampleDataList = trafficAction.getSampleDataList();
        return SUCCESS.toUpperCase();
    }

    public String createSampleDataJson() {
        try {
            if (sampleResponseString == null || sampleRequestString == null) {
                addActionError("request and response cannot be null");
                return ERROR.toUpperCase();
            }
            String[] requestLines = sampleRequestString.split("\n");
            Map<String, Object> map = new HashMap<>();
            int requestIndex = 0;
            String[] requestURL = requestLines[requestIndex].split(" ");
            map.put(METHOD, requestURL[0].trim());
            map.put(TYPE, requestURL[2].trim());

            String[] requestHost = requestLines[++requestIndex].split(":");
            String host = requestHost[1].trim();
            map.put(PATH, host + requestURL[1].trim());

            Map<String, String> requestHeaders = new HashMap<>();
            for (requestIndex = requestIndex+1; requestIndex < requestLines.length; requestIndex++) {
                String[] requestHeader = requestLines[requestIndex].split(":",2);
                if (requestHeader.length == 2) {
                    requestHeaders.put(requestHeader[0].trim(), requestHeader[1].trim());
                } else {
                    break;
                }
            }
            map.put(REQUEST_HEADERS, gson.toJson(requestHeaders));
            if (requestIndex + 1 < requestLines.length) {
                StringBuilder requestPayload = new StringBuilder();
                for (int i = requestIndex + 1; i < requestLines.length; i++) {
                    requestPayload.append(requestLines[i].trim()).append("\n");
                }
                map.put(REQUEST_PAYLOAD, requestPayload.toString());
            } else {
                map.put(REQUEST_PAYLOAD, "");
            }
            map.put(AKTO_VXLAN_ID, 0);

            String[] responseLines = sampleResponseString.split("\n");
            int responseIndex = 0;
            String[] responseStatus = responseLines[responseIndex].split(" ",3);

            map.put(STATUS_CODE, responseStatus[1].trim());
            map.put(STATUS, responseStatus[2].trim());

            Map<String, String> responseHeaders = new HashMap<>();
            for (responseIndex = responseIndex+1; responseIndex < responseLines.length; responseIndex++) {
                String[] responseHeader = responseLines[responseIndex].split(":",2);
                if (responseHeader.length == 2) {
                    responseHeaders.put(responseHeader[0].trim(), responseHeader[1].trim());
                } else {
                    break;
                }
            }
            map.put(RESPONSE_HEADERS, gson.toJson(responseHeaders));
            if (responseIndex + 1 < responseLines.length) {
                StringBuilder builder = new StringBuilder();
                for (responseIndex = responseIndex+1; responseIndex < responseLines.length; responseIndex++) {
                    builder.append(responseLines[responseIndex].trim()).append("\n");
                }
                map.put(RESPONSE_PAYLOAD, builder.toString());
            } else {
                map.put(RESPONSE_PAYLOAD, "");
            }
            map.put("source", HttpResponseParams.Source.OTHER);
            map.put("time", Context.now());
            map.put("ip", "null");
            map.put("akto_account_id", "1000000");

            SampleData sampleData = new SampleData();
            Key key = new Key(0, (String) map.get(PATH), URLMethods.Method.fromString((String) map.get(METHOD)),
                    Integer.parseInt((String) map.get(STATUS_CODE)),0,0);

            sampleData.setId(key);
            sampleData.setSamples(Collections.singletonList(gson.toJson(map)));
            sampleDataList = Collections.singletonList(sampleData);
        } catch (Exception e) {
            addActionError("Please check your request and response format");
            return ERROR.toUpperCase();
        }
        return SUCCESS.toUpperCase();
    }

    @Override
    public void setServletRequest(HttpServletRequest request) {
        this.request = request;
    }

    @Override
    public void setServletResponse(HttpServletResponse response) {
        this.response = response;
    }

    public ArrayList<BasicDBObject> getSubCategories() {
        return subCategories;
    }

    public void setSubCategories(ArrayList<BasicDBObject> subCategories) {
        this.subCategories = subCategories;
    }

    public GlobalEnums.TestCategory[] getCategories() {
        return categories;
    }

    public void setCategories(GlobalEnums.TestCategory[] categories) {
        this.categories = categories;
    }

    public List<TestSourceConfig> getTestSourceConfigs() {
        return testSourceConfigs;
    }

    public void setTestSourceConfigs(List<TestSourceConfig> testSourceConfigs) {
        this.testSourceConfigs = testSourceConfigs;
    }

    public List<VulnerableRequestForTemplate> getVulnerableRequests() {
        return vulnerableRequests;
    }

    public void setVulnerableRequests(List<VulnerableRequestForTemplate> vulnerableRequests) {
        this.vulnerableRequests = vulnerableRequests;
    }

    public List<SampleData> getSampleDataList() {
        return sampleDataList;
    }

    public void setSampleDataList(List<SampleData> sampleDataList) {
        this.sampleDataList = sampleDataList;
    }

    public int getApiCollectionId() {
        return apiCollectionId;
    }

    public void setApiCollectionId(int apiCollectionId) {
        this.apiCollectionId = apiCollectionId;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public void setSampleRequestString(String sampleRequestString) {
        this.sampleRequestString = sampleRequestString;
    }

    public void setSampleResponseString(String sampleResponseString) {
        this.sampleResponseString = sampleResponseString;
    }

    public String getSampleRequestString() {
        return sampleRequestString;
    }

    public String getSampleResponseString() {
        return sampleResponseString;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public BasicDBObject getApiInfoKey() {
        return apiInfoKey;
    }

    public void setApiInfoKey(BasicDBObject apiInfoKey) {
        this.apiInfoKey = apiInfoKey;
    }

    public TestingRunResult getTestingRunResult() {
        return testingRunResult;
    }

    public void setTestingRunResult(TestingRunResult testingRunResult) {
        this.testingRunResult = testingRunResult;
    }

    public TestingRunIssues getTestingRunIssues() {
        return testingRunIssues;
    }

    public void setTestingRunIssues(TestingRunIssues testingRunIssues) {
        this.testingRunIssues = testingRunIssues;
    }

    public Map<String, BasicDBObject> getSubCategoryMap() {
        return subCategoryMap;
    }

    public void setSubCategoryMap(Map<String, BasicDBObject> subCategoryMap) {
        this.subCategoryMap = subCategoryMap;
    }
}
