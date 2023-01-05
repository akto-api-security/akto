package com.akto.rules;

import com.akto.dto.ApiInfo;
import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.OriginalHttpResponse;
import com.akto.dto.RawApi;
import com.akto.dto.testing.TestResult;
import com.akto.dto.testing.info.NucleiTestInfo;
import com.akto.store.SampleMessageStore;
import com.akto.store.TestingUtil;
import com.akto.testing.NucleiExecutor;
import com.akto.testing.StatusCodeAnalyser;
import com.akto.util.Pair;
import com.google.common.io.Files;

import com.google.gson.JsonObject;
import com.mongodb.BasicDBObject;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

public class FuzzingTest extends TestPlugin {

    private final String testRunId;
    private final String testRunResultSummaryId;
    private final String origTemplatePath;
    private String tempTemplatePath;
    private final String subcategory;

    public FuzzingTest(String testRunId, String testRunResultSummaryId, String origTemplatePath, String subcategory) {
        this.testRunId = testRunId;
        this.testRunResultSummaryId = testRunResultSummaryId;
        this.origTemplatePath = origTemplatePath;
        this.subcategory = subcategory;
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
        if (messages.isEmpty()) return null;

        RawApi rawApi = messages.get(0);
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
            downloadLinks(this.tempTemplatePath, outputDir);
        } catch (IOException e1) {
            return addWithRequestError( rawApi.getOriginalMessage(), TestResult.TestError.API_REQUEST_FAILED, testRequest, nucleiTestInfo);
        }

        try {
            NucleiExecutor.NucleiResult nucleiResult = NucleiExecutor.execute(
                testRequest.getMethod(), 
                testRequest.getFullUrlWithParams(), 
                this.tempTemplatePath,
                outputDir, 
                testRequest.getBody(), 
                testRequest.getHeaders(),
                pwd
            );

            if (nucleiResult == null) return addWithoutRequestError(rawApi.getOriginalMessage(), TestResult.TestError.FAILED_BUILDING_NUCLEI_TEMPLATE);

            int index = 0;
            for (Pair<OriginalHttpRequest, OriginalHttpResponse> pair: nucleiResult.attempts) {
                OriginalHttpResponse testResponse = pair.getSecond();

                int statusCode = StatusCodeAnalyser.getStatusCode(testResponse.getBody(), testResponse.getStatusCode());
                double percentageMatch = compareWithOriginalResponse(originalHttpResponse.getBody(), testResponse.getBody());

                vulnerable = nucleiResult.metaData.get(index).getBoolean("matcher-status"); // todo:

                apiExecutionDetails = new ApiExecutionDetails(statusCode, percentageMatch, testResponse);

                TestResult testResult = buildTestResult(
                    pair.getFirst(), apiExecutionDetails.testResponse, rawApi.getOriginalMessage(), apiExecutionDetails.percentageMatch, vulnerable, nucleiTestInfo
                );
        
                testResults.add(testResult);
                index+=1;

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


    @Override
    public String superTestName() {
        return "FUZZING";
    }

    @Override
    public String subTestName() {
        return "CUSTOM_IAM";
    }
}
