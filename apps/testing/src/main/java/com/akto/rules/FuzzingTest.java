package com.akto.rules;

import com.akto.dto.ApiInfo;
import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.OriginalHttpResponse;
import com.akto.dto.RawApi;
import com.akto.dto.testing.TestResult;
import com.akto.dto.testing.info.NucleiTestInfo;
import com.akto.store.TestingUtil;
import com.akto.testing.NucleiExecutor;
import com.akto.testing.StatusCodeAnalyser;
import com.akto.util.Pair;
import com.google.common.io.Files;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

public class FuzzingTest extends AuthRequiredTestPlugin {

    private String testRunId;
    private String testRunResultSummaryId;
    private String origTemplatePath;
    private String tempTemplatePath;
    private String subcategory;

    public FuzzingTest(String testRunId, String testRunResultSummaryId, String origTemplatePath, String subcategory) {
        this.testRunId = testRunId;
        this.testRunResultSummaryId = testRunResultSummaryId;
        this.origTemplatePath = origTemplatePath;
        this.subcategory = subcategory;
        this.tempTemplatePath = null;
    }

    private static File createDirPath(String filePath) {
        try {
            File file = new File(filePath);
            Files.createParentDirs(file);
            return file;
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    public Result exec(ApiInfo.ApiInfoKey apiInfoKey, TestingUtil testingUtil, List<RawApi> filteredMessages) {
        RawApi rawApi = filteredMessages.get(0);
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
            return addWithRequestError( rawApi.getOriginalMessage(), TestResult.TestError.API_REQUEST_FAILED, testRequest, nucleiTestInfo);
        }


        try {            
            ArrayList<Pair<OriginalHttpRequest, OriginalHttpResponse>> attempts = NucleiExecutor.execute(
                testRequest.getMethod(), 
                testRequest.getFullUrlWithParams(), 
                this.tempTemplatePath,
                outputDir, 
                testRequest.getBody(), 
                testRequest.getHeaders()
            );

            for (Pair<OriginalHttpRequest, OriginalHttpResponse> pair: attempts) {
                OriginalHttpResponse testResponse = pair.getSecond();

                int statusCode = StatusCodeAnalyser.getStatusCode(testResponse.getBody(), testResponse.getStatusCode());
                double percentageMatch = compareWithOriginalResponse(originalHttpResponse.getBody(), testResponse.getBody());
        
                apiExecutionDetails = new ApiExecutionDetails(statusCode, percentageMatch, testResponse);

                vulnerable = isStatusGood(apiExecutionDetails.statusCode) && apiExecutionDetails.percentageMatch < 50;

                TestResult testResult = buildTestResult(
                    pair.getFirst(), apiExecutionDetails.testResponse, rawApi.getOriginalMessage(), apiExecutionDetails.percentageMatch, vulnerable, nucleiTestInfo
                );
        
                testResults.add(testResult);

            }
        } catch (Exception e) {
            e.printStackTrace();
            return addWithRequestError( rawApi.getOriginalMessage(), TestResult.TestError.API_REQUEST_FAILED, testRequest, nucleiTestInfo);
        }

        return addTestSuccessResult(vulnerable, testResults, new ArrayList<>(), TestResult.Confidence.HIGH);    }


    @Override
    public String superTestName() {
        return "FUZZING";
    }

    @Override
    public String subTestName() {
        return "CUSTOM_IAM";
    }
}
