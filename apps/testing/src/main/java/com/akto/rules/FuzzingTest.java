package com.akto.rules;

import com.akto.dto.ApiInfo;
import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.OriginalHttpResponse;
import com.akto.dto.RawApi;
import com.akto.dto.testing.TestResult;
import com.akto.dto.testing.info.NucleiTestInfo;
import com.akto.store.SampleMessageStore;
import com.akto.store.TestingUtil;
import com.akto.testing.ApiExecutor;
import com.akto.testing.NucleiExecutor;
import com.akto.testing.StatusCodeAnalyser;
import com.akto.util.Pair;
import com.akto.utils.RedactSampleData;
import com.google.common.io.Files;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.net.URL;
import java.util.*;

public class FuzzingTest extends TestPlugin {

    private final String testRunId;
    private final String testRunResultSummaryId;
    private final String origTemplatePath;
    private String tempTemplatePath;
    private final String subcategory;
    private String testSourceConfigCategory;

    public FuzzingTest(String testRunId, String testRunResultSummaryId, String origTemplatePath, String subcategory, String testSourceConfigCategory) {
        this.testRunId = testRunId;
        this.testRunResultSummaryId = testRunResultSummaryId;
        this.origTemplatePath = origTemplatePath;
        this.subcategory = subcategory;
        this.testSourceConfigCategory = testSourceConfigCategory;
        this.tempTemplatePath = null;
    }

    public static File createDirPath(String filePath) {
        try {
            File file = new File(filePath);
            Files.createParentDirs(file);
            return file;
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    public Result start(ApiInfo.ApiInfoKey apiInfoKey, TestingUtil testingUtil) {
        List<RawApi> messages = SampleMessageStore.fetchAllOriginalMessages(apiInfoKey, testingUtil.getSampleMessages());
        RawApi rawApi;
        if (messages.isEmpty()) {
            OriginalHttpRequest originalHttpRequest = new OriginalHttpRequest(
                    apiInfoKey.getUrl(), null, apiInfoKey.method.name(), null, new HashMap<>(), ""
            );

            OriginalHttpResponse originalHttpResponse = null;
            try {
                originalHttpResponse = ApiExecutor.sendRequest(originalHttpRequest,true);
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
            String originalMessage = RedactSampleData.convertOriginalReqRespToString(originalHttpRequest, originalHttpResponse);
            rawApi = new RawApi(originalHttpRequest, originalHttpResponse, originalMessage);
        } else {
            rawApi = messages.get(0);
        }

        List<TestResult> testResults = new ArrayList<>();
        OriginalHttpRequest testRequest = rawApi.getRequest().copy();

        OriginalHttpResponse originalHttpResponse = rawApi.getResponse().copy();

        ApiExecutionDetails apiExecutionDetails;

        String subDir = ""+testRequest.hashCode();

        String pwd = new File("").getAbsolutePath();

        String filepath = StringUtils.join(new String[]{pwd, testRunId, testRunResultSummaryId, subDir, "logs.txt"}, "/");
        File file = createDirPath(filepath);

        if (file == null) return null;
        boolean vulnerable = false;
        String outputDir = file.getParent();
        this.tempTemplatePath = outputDir+"/"+subcategory+".yaml";
        NucleiTestInfo nucleiTestInfo = new NucleiTestInfo(this.subcategory, this.origTemplatePath);

        try {
            FileUtils.copyURLToFile(new URL(this.origTemplatePath), new File(this.tempTemplatePath));
        } catch (IOException e1) {
            e1.printStackTrace();
            return addWithRequestError( rawApi.getOriginalMessage(), TestResult.TestError.FAILED_DOWNLOADING_NUCLEI_TEMPLATE, testRequest, nucleiTestInfo);
        }

        try {
            downloadLinks(this.tempTemplatePath, outputDir);
        } catch (Exception e) {
            e.printStackTrace();
            return addWithRequestError( rawApi.getOriginalMessage(), TestResult.TestError.FAILED_DOWNLOADING_PAYLOAD_FILES, testRequest, nucleiTestInfo);
        }

        String fullUrlWithHost;
        try {
             fullUrlWithHost = testRequest.getFullUrlIncludingDomain();
        } catch (Exception e) {
            e.printStackTrace();
            return addWithRequestError( rawApi.getOriginalMessage(), TestResult.TestError.FAILED_BUILDING_URL_WITH_DOMAIN, testRequest, nucleiTestInfo);
        }

        try {
            NucleiExecutor.NucleiResult nucleiResult = NucleiExecutor.execute(
                testRequest.getMethod(), 
                fullUrlWithHost,
                this.tempTemplatePath,
                outputDir, 
                testRequest.getBody(), 
                testRequest.getHeaders(),
                pwd
            );

            if (nucleiResult == null) return addWithoutRequestError(rawApi.getOriginalMessage(), TestResult.TestError.FAILED_BUILDING_NUCLEI_TEMPLATE);

            int totalCount = Math.min(Math.min(nucleiResult.attempts.size(), nucleiResult.metaData.size()), 100);

            for (int idx=0; idx < totalCount; idx++) {
                Pair<OriginalHttpRequest, OriginalHttpResponse> pair = nucleiResult.attempts.get(idx);
                OriginalHttpResponse testResponse = pair.getSecond();

                int statusCode = StatusCodeAnalyser.getStatusCode(testResponse.getBody(), testResponse.getStatusCode());
                double percentageMatch = compareWithOriginalResponse(originalHttpResponse.getBody(), testResponse.getBody(), new HashMap<>());

                vulnerable = vulnerable || nucleiResult.metaData.get(idx).getBoolean("matcher-status");

                apiExecutionDetails = new ApiExecutionDetails(statusCode, percentageMatch, testResponse, originalHttpResponse, rawApi.getOriginalMessage());

                TestResult testResult = buildTestResult(
                    pair.getFirst(), apiExecutionDetails.testResponse, rawApi.getOriginalMessage(), apiExecutionDetails.percentageMatch, vulnerable, nucleiTestInfo
                );
        
                testResults.add(testResult);

            }
        } catch (Exception e) {
            e.printStackTrace();
            return addWithRequestError( rawApi.getOriginalMessage(), TestResult.TestError.API_REQUEST_FAILED, testRequest, nucleiTestInfo);
        }

        return addTestSuccessResult(vulnerable, testResults, new ArrayList<>(), TestResult.Confidence.HIGH);
    }

    public static void downloadLinks(String templatePath, String outputDir) throws IOException {
        List<String> urlsToDownload = new ArrayList<>();
        Yaml yaml = new Yaml();
        InputStream inputStream = java.nio.file.Files.newInputStream(new File(templatePath).toPath());
        Map<String, Object> data = yaml.load(inputStream);
        if (data == null) data = new HashMap<>();
        List<Map<String, Object>> requests = (List<Map<String, Object>>) data.get("requests");
        if (requests == null) requests = new ArrayList<>();
        for (Map<String,Object> request: requests) {
            Map<String, Object> payloads = (Map<String, Object>) request.get("payloads");
            if (payloads == null) payloads = new HashMap<>();
            for (Object payload: payloads.values()) {
                if (payload.getClass().equals(String.class)) {
                    String payloadString = (String) payload;
                    if (payloadString.startsWith("http")) urlsToDownload.add(payloadString);
                }
            }
        }

        for (String url: urlsToDownload) {
            String[] fileNameList = url.split("/");
            FileUtils.copyURLToFile(new URL(url), new File(outputDir + "/" + fileNameList[fileNameList.length-1]));
        }
    }

    public String getSubcategory() {
        return subcategory;
    }

    @Override
    public String superTestName() {
        return "FUZZING";
    }

    @Override
    public String subTestName() {
        return "CUSTOM_IAM";
    }

    public String getTestSourceConfigCategory() {
        return testSourceConfigCategory;
    }

    public void setTestSourceConfigCategory(String testSourceConfigCategory) {
        this.testSourceConfigCategory = testSourceConfigCategory;
    }
}
