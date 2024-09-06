package com.akto;

import com.akto.data_actor.DataActor;
import com.akto.data_actor.DataActorFactory;
import com.akto.dto.CodeAnalysisApi;
import com.akto.dto.CodeAnalysisRepo;
import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.OriginalHttpResponse;
import com.akto.log.LoggerMaker;
import com.akto.testing.ApiExecutor;
import com.akto.util.http_util.CoreHTTPClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import okhttp3.*;

import javax.net.ssl.X509TrustManager;
import java.io.*;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


public class Main {
    public static final String BITBUCKET_URL = System.getenv("BITBUCKET_HOST");
    public static final String SOURCE_CODE_HOST = System.getenv("SOURCE_CODE_HOST");
    public static final String BITBUCKET_TOKEN = System.getenv("BITBUCKET_TOKEN");
    public static final String ALL_PROJECT_LIST = "ALL_PROJECT_LIST";
    public static final String REPO_BRANCH_URL = "REPO_BRANCH_URL";
    public static final String SOURCE_CODE_URL = "SOURCE_CODE_URL";
    private static final LoggerMaker loggerMaker = new LoggerMaker(Main.class, LoggerMaker.LogDb.RUNTIME);
    private static final DataActor dataActor = DataActorFactory.fetchInstance();
    private static final ObjectMapper objectMapper = new ObjectMapper();
    public static final int MAX_BATCH_SIZE = 100;
    private static final int SLEEP_TIME = 10 * 1000;
    private static final OkHttpClient client = CoreHTTPClient.client.newBuilder()
            .connectTimeout(0, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS)
            .connectionPool(new ConnectionPool(256, 5L, TimeUnit.MINUTES))
            .sslSocketFactory(CoreHTTPClient.trustAllSslSocketFactory, (X509TrustManager)CoreHTTPClient.trustAllCerts[0])
            .hostnameVerifier((hostname, session) -> true)
            .followRedirects(false).build();

    private static final Map<String, String> allApiEndpoints = new HashMap<>();
    static {
        allApiEndpoints.put(ALL_PROJECT_LIST, BITBUCKET_URL + "/rest/api/1.0/projects");
        allApiEndpoints.put(SOURCE_CODE_URL, SOURCE_CODE_HOST + "/api/run-analyser");
        allApiEndpoints.put(REPO_BRANCH_URL, BITBUCKET_URL + "/rest/api/1.0/projects/__PROJECT_KEY__/repos/__REPO_NAME__/archive");
    }

    public static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    private static Map<String, List<String>> buildHeaders() {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("Authorization", Collections.singletonList("Bearer " + BITBUCKET_TOKEN));
        return headers;
    }

    private static List<CodeAnalysisRepo> fetchReposToSync() {
        List<CodeAnalysisRepo> repos = dataActor.findReposToRun();
        if (repos!= null && !repos.isEmpty()) {
            return repos;
        }
        return null;
    }

    private static Map<String, String> fetchAllProjectKeys() {
        Map<String, List<String>> headers = buildHeaders();
        OriginalHttpRequest request = new OriginalHttpRequest(allApiEndpoints.get(ALL_PROJECT_LIST), "", "GET", null, headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null, true);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("non 2xx response in fetchAllProjectKeys");
                return null;
            }
            BasicDBObject payloadObj;
            try {
                payloadObj =  BasicDBObject.parse(responsePayload);
                BasicDBList values = (BasicDBList) payloadObj.get("values");
                Map<String, String> projectNameVsKey = new HashMap<>();
                values.forEach(projectDetailObj -> {
                    BasicDBObject projectDetails = (BasicDBObject) projectDetailObj;
                    projectNameVsKey.put(projectDetails.getString("name"), projectDetails.getString("key"));
                });
                if (!projectNameVsKey.isEmpty()) {
                    return projectNameVsKey;
                }
            } catch(Exception e) {
                loggerMaker.errorAndAddToDb("failed while parsing response for fetchAllProjectKeys");
                return null;
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in fetchEstimatedDocCount" + e);
            return null;
        }
        return null;
    }

    public static void main(String[] args) throws InterruptedException {
        while (true) {
            List<CodeAnalysisRepo> repos = fetchReposToSync();
            if (repos == null) {
                loggerMaker.infoAndAddToDb("No repos to run, skipping");
                Thread.sleep(SLEEP_TIME);
                continue;
            }
            Map<String, String>  allProjectKeys = fetchAllProjectKeys();
            if (allProjectKeys == null) {
                loggerMaker.infoAndAddToDb("No project Keys found skipping");
                Thread.sleep(SLEEP_TIME);
                continue;
            }

            for (CodeAnalysisRepo repo : repos) {

                String repoUrl = allApiEndpoints.get(REPO_BRANCH_URL).replace("__PROJECT_KEY__", allProjectKeys.get(repo.getProjectName()))
                        .replace("__REPO_NAME__",repo.getRepoName());

                String outputFilePath = repo.getRepoName()+ ".zip"; // The local file where the repository will be saved
                File file = new File(outputFilePath);

                Request.Builder builder = new Request.Builder();
                builder.url(repoUrl);
                builder.get();
                builder.addHeader("Authorization", "Bearer " + BITBUCKET_TOKEN);
                Request request = builder.build();
                try {
                    Response response = client.newCall(request).execute();
                    if (response.isSuccessful() && response.body() != null) {
                        // Get input stream from the response body
                        InputStream inputStream = Objects.requireNonNull(response.body()).byteStream();

                        // Write the input stream to a file
                        try (FileOutputStream fileOutputStream = new FileOutputStream(file)) {
                            byte[] buffer = new byte[1024];
                            int bytesRead;
                            while ((bytesRead = inputStream.read(buffer)) != -1) {
                                fileOutputStream.write(buffer, 0, bytesRead);
                            }
                            fileOutputStream.flush();
                            fileOutputStream.close();
                            loggerMaker.infoAndAddToDb("Repository downloaded successfully.");
                            BasicDBObject requestBody = new BasicDBObject();
                            requestBody.put("path", file.getAbsolutePath());
                            requestBody.put("projectKey",allProjectKeys.get(repo.getProjectName()));
                            requestBody.put("repoName",repo.getRepoName());
                            requestBody.put("bitbucketHost",BITBUCKET_URL);
                            requestBody.put("is_bitbucket",true);
                            OriginalHttpRequest originalHttpRequest = new OriginalHttpRequest(allApiEndpoints.get(SOURCE_CODE_URL), "", "POST", requestBody.toJson(), null, "");
                            OriginalHttpResponse originalHttpResponse = ApiExecutor.sendRequest(originalHttpRequest, true, null, false, null, true);
                            String responsePayload = originalHttpResponse.getBody();
                            if (originalHttpResponse.getStatusCode() != 200 || responsePayload == null) {
                                loggerMaker.errorAndAddToDb("non 2xx response in fetching apis from sourcecode", LoggerMaker.LogDb.RUNTIME);
                                continue;
                            }
                            syncRepoToDashboard(originalHttpResponse.getBody(), repo);
                        }
                    } else {
                        loggerMaker.errorAndAddToDb("Failed to download repository "+ repo.getRepoName() +". Response code: " + response.code());
                    }
                } catch (Exception e) {
                    loggerMaker.errorAndAddToDb("error while calling api" + e.getMessage());
                }

            }
            Thread.sleep(SLEEP_TIME);
        }

    }

    private static void syncRepoToDashboard(String body, CodeAnalysisRepo repo) {
        try {
            BasicDBObject requestBody = BasicDBObject.parse(body);
            BasicDBList basicDBList = (BasicDBList) requestBody.get("codeAnalysisApisList");
            List<CodeAnalysisApi> codeAnalysisApiList = new ArrayList<>();
            for (Object obj : basicDBList) {
                BasicDBObject codeAnalysisApi = (BasicDBObject) obj;
                CodeAnalysisApi s = objectMapper.readValue(codeAnalysisApi.toJson(), CodeAnalysisApi.class);
                if (codeAnalysisApiList.size() <= MAX_BATCH_SIZE) {
                    codeAnalysisApiList.add(s);
                } else {
                    dataActor.syncExtractedAPIs(repo, codeAnalysisApiList, false);
                    codeAnalysisApiList.clear();
                }
            }
            if (codeAnalysisApiList.size() > 0) {
                dataActor.syncExtractedAPIs(repo, codeAnalysisApiList, true);
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Failed to parse request body");
        }
    }
}