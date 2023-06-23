package com.akto.rules;

import com.akto.dao.context.Context;
import com.akto.dto.ApiInfo;
import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.OriginalHttpResponse;
import com.akto.dto.RawApi;
import com.akto.dto.testing.TestResult;
import com.akto.dto.testing.TestingRunConfig;
import com.akto.dto.testing.info.NucleiTestInfo;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.store.SampleMessageStore;
import com.akto.store.TestingUtil;
import com.akto.testing.ApiExecutor;
import com.akto.testing.NucleiExecutor;
import com.akto.testing.StatusCodeAnalyser;
import com.akto.util.Pair;
import com.akto.utils.RedactSampleData;
import com.google.common.io.Files;

import kotlin.text.Charsets;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.attribute.FileTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FuzzingTest extends TestPlugin {

    private final String testRunId;
    private final String testRunResultSummaryId;
    private final String origTemplatePath;
    private String tempTemplatePath;
    private final String subcategory;
    private String testSourceConfigCategory;
    private final Map<String, Object> valuesMap;
    private static final int ONE_DAY = 24*60*60;
    public static final int payloadLineLimit = 100;
    private static final int CONNECTION_TIMEOUT = 10 * 1000;

    public FuzzingTest(String testRunId, String testRunResultSummaryId, String origTemplatePath, String subcategory,
                       String testSourceConfigCategory, Map<String, Object> valuesMap) {
        this.testRunId = testRunId;
        this.testRunResultSummaryId = testRunResultSummaryId;
        this.origTemplatePath = origTemplatePath;
        this.subcategory = subcategory;
        this.testSourceConfigCategory = testSourceConfigCategory;
        this.tempTemplatePath = null;
        this.valuesMap = valuesMap;
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

    private static boolean downloadFileCheck(String filePath){
        try {
            FileTime fileTime = java.nio.file.Files.getLastModifiedTime(new File(filePath).toPath());
            if(fileTime.toMillis()/1000l >= (Context.now()-ONE_DAY)){
                return false;
            }
        } catch (Exception e){
            return true;
        }
        return true;
    }

    @Override
    public Result start(ApiInfo.ApiInfoKey apiInfoKey, TestingUtil testingUtil, TestingRunConfig testingRunConfig) {
        List<RawApi> messages = testingUtil.getSampleMessageStore().fetchAllOriginalMessages(apiInfoKey, testingUtil.getSampleMessages());
        RawApi rawApi;
        if (messages.isEmpty()) {
            OriginalHttpRequest originalHttpRequest = new OriginalHttpRequest(
                    apiInfoKey.getUrl(), null, apiInfoKey.method.name(), null, new HashMap<>(), ""
            );

            OriginalHttpResponse originalHttpResponse = null;
            try {
                originalHttpResponse = ApiExecutor.sendRequest(originalHttpRequest,true, testingRunConfig);
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb("Error while after executing " + subTestName() +"test : " + e, LogDb.TESTING);
                return null;
            }
            String originalMessage = RedactSampleData.convertOriginalReqRespToString(originalHttpRequest, originalHttpResponse);
            rawApi = new RawApi(originalHttpRequest, originalHttpResponse, originalMessage);
        } else {
            rawApi = messages.get(0);
        }

        return runNucleiTest(rawApi, testingRunConfig);
    }

    public Result runNucleiTest(RawApi rawApi, TestingRunConfig testingRunConfig) {
        OriginalHttpRequest testRequest = rawApi.getRequest().copy();
        OriginalHttpResponse originalHttpResponse = rawApi.getResponse().copy();
        String originalMessage = rawApi.getOriginalMessage();
        List<TestResult> testResults = new ArrayList<>();
        ApiExecutionDetails apiExecutionDetails;

        String subDir = ""+testRequest.hashCode();

        String pwd = new File("").getAbsolutePath();

        String filepath = StringUtils.join(new String[]{pwd, testRunId, testRunResultSummaryId, subDir, "logs.txt"}, "/");
        File file = createDirPath(filepath);

        if (file == null) return null;
        boolean overallVulnerable = false;
        String outputDir = file.getParent();
        this.tempTemplatePath = outputDir+"/"+subcategory+".yaml";
        NucleiTestInfo nucleiTestInfo = new NucleiTestInfo(this.subcategory, this.origTemplatePath);

        try {
            if(downloadFileCheck(this.tempTemplatePath)){
            FileUtils.copyURLToFile(new URL(this.origTemplatePath), new File(this.tempTemplatePath), CONNECTION_TIMEOUT, CONNECTION_TIMEOUT);
            }
        } catch (IOException e1) {
            e1.printStackTrace();
            return addWithRequestError( originalMessage, TestResult.TestError.FAILED_DOWNLOADING_NUCLEI_TEMPLATE, testRequest, nucleiTestInfo);
        }

        try {
            downloadLinks(this.tempTemplatePath, outputDir);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error while downloading links: " + e, LogDb.TESTING);
            return addWithRequestError( rawApi.getOriginalMessage(), TestResult.TestError.FAILED_DOWNLOADING_PAYLOAD_FILES, testRequest, nucleiTestInfo);
        }

        String fullUrlWithHost;
        try {
            fullUrlWithHost = testRequest.getFullUrlIncludingDomain();

            fullUrlWithHost = ApiExecutor.replaceHostFromConfig(fullUrlWithHost, testingRunConfig);


        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error while getting full url including domain: " + e, LogDb.TESTING);
            return addWithRequestError( rawApi.getOriginalMessage(), TestResult.TestError.FAILED_BUILDING_URL_WITH_DOMAIN, testRequest, nucleiTestInfo);
        }

        try {
            replaceVariables(this.tempTemplatePath, valuesMap);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error while replacing variables in nuclei file: " + e, LogDb.TESTING);
            return addWithRequestError( originalMessage, TestResult.TestError.FAILED_BUILDING_URL_WITH_DOMAIN, testRequest, nucleiTestInfo);
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

            if (nucleiResult == null) return addWithoutRequestError(originalMessage, TestResult.TestError.FAILED_BUILDING_NUCLEI_TEMPLATE);

            int totalCount = Math.min(Math.min(nucleiResult.attempts.size(), nucleiResult.metaData.size()), 100);

            List<TestResult> vulnerableTestResults = new ArrayList<>();
            List<TestResult> nonVulnerableTestResults = new ArrayList<>();

            for (int idx=0; idx < totalCount; idx++) {
                Pair<OriginalHttpRequest, OriginalHttpResponse> pair = nucleiResult.attempts.get(idx);
                OriginalHttpResponse testResponse = pair.getSecond();

                int statusCode = StatusCodeAnalyser.getStatusCode(testResponse.getBody(), testResponse.getStatusCode());
                double percentageMatch = compareWithOriginalResponse(originalHttpResponse.getBody(), testResponse.getBody(), new HashMap<>());
                boolean vulnerable = nucleiResult.metaData.get(idx).getBoolean("matcher-status");

                overallVulnerable = overallVulnerable || vulnerable;

                apiExecutionDetails = new ApiExecutionDetails(statusCode, percentageMatch, testResponse, originalHttpResponse, originalMessage);

                TestResult testResult = buildTestResult(
                        pair.getFirst(), apiExecutionDetails.testResponse, originalMessage, apiExecutionDetails.percentageMatch, vulnerable, nucleiTestInfo
                );

                if (vulnerable) {
                    vulnerableTestResults.add(testResult);
                } else {
                    nonVulnerableTestResults.add(testResult);
                }
            }

            vulnerableTestResults.addAll(nonVulnerableTestResults);
            testResults = vulnerableTestResults;

        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error while running nuclei test: " + e, LogDb.TESTING);
            return addWithRequestError( rawApi.getOriginalMessage(), TestResult.TestError.API_REQUEST_FAILED, testRequest, nucleiTestInfo);
        }

        return addTestSuccessResult(overallVulnerable, testResults, new ArrayList<>(), TestResult.Confidence.HIGH);
    }

    public static void replaceVariables(String templatePath, Map<String, Object> valuesMap) throws Exception {
        if (valuesMap == null || valuesMap.isEmpty()) return;

        InputStream inputStream = java.nio.file.Files.newInputStream(new File(templatePath).toPath());
        StringBuilder textBuilder = new StringBuilder();
        try (Reader reader = new BufferedReader(new InputStreamReader
                (inputStream, Charset.forName(StandardCharsets.UTF_8.name())))) {
            int c = 0;
            while ((c = reader.read()) != -1) {
                textBuilder.append((char) c);
            }
        }

        String templateString = textBuilder.toString();

        String regex = "\\{\\{(.*?)\\}\\}"; // todo: integer inside brackets
        Pattern p = Pattern.compile(regex);

        // replace with values
        Matcher matcher = p.matcher(templateString);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String key = matcher.group(1);
            if (key == null) continue;
            if (!valuesMap.containsKey(key)) {
                System.out.println("Missed: " + key);
                continue;
            }
            Object obj = valuesMap.get(key);
            if (obj == null) obj = "null";

            String val = obj.toString();
            if (true) { // todo remove
                val = val.replace("\\", "\\\\")
                        .replace("\t", "\\t")
                        .replace("\b", "\\b")
                        .replace("\n", "\\n")
                        .replace("\r", "\\r")
                        .replace("\f", "\\f")
                        .replace("\'", "\\'")
                        .replace("\"", "\\\"");
            }
            matcher.appendReplacement(sb, "");
            sb.append(val);
        }

        matcher.appendTail(sb);

        String finalTemplateString = sb.toString();


        File file = new File(templatePath);
        FileUtils.writeStringToFile(file, finalTemplateString, Charsets.UTF_8);
        inputStream.close();
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

            List<String> lines = new ArrayList<>();
            URLConnection urlConnection = new URL(url).openConnection();
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()));
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                lines.add(line);
            }
            bufferedReader.close();

            if (lines.size() > payloadLineLimit) {
                Collections.shuffle(lines);
                lines = lines.subList(0, payloadLineLimit);
            }

            FileUtils.writeLines(new File(outputDir + "/" + fileNameList[fileNameList.length-1]), StandardCharsets.UTF_8.name(), lines);
        }
        inputStream.close();
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
