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
import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.commons.lang3.StringUtils;

import javax.net.ssl.X509TrustManager;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

abstract public class SourceCodeAnalyserRepo {
    private CodeAnalysisRepo repoToBeAnalysed;
    private boolean isAktoGPTEnabled;
    private static final LoggerMaker loggerMaker = new LoggerMaker(SourceCodeAnalyserRepo.class, LoggerMaker.LogDb.RUNTIME);
    abstract public String getToken();
    abstract public String getRepoUrl();
    abstract public BasicDBObject getCodeAnalysisBody(String path);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    public static final int MAX_BATCH_SIZE = 100;
    protected static final DataActor dataActor = DataActorFactory.fetchInstance();
    public static final String SOURCE_CODE_HOST = System.getenv("SOURCE_CODE_HOST");

    private static String getFullUrl(String path){
        if (!StringUtils.isEmpty(SOURCE_CODE_HOST)) {
            return SOURCE_CODE_HOST + path; 
        } else {
            return null;
        }
    }

    private static final OkHttpClient client = CoreHTTPClient.client.newBuilder()
            .connectTimeout(0, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS)
            .connectionPool(new ConnectionPool(256, 5L, TimeUnit.MINUTES))
            .sslSocketFactory(CoreHTTPClient.trustAllSslSocketFactory, (X509TrustManager)CoreHTTPClient.trustAllCerts[0])
            .hostnameVerifier((hostname, session) -> true)
            .followRedirects(true).build();

    public String downloadRepository () {
        String finalUrl = this.getRepoUrl();
        String token = this.getToken();
        if (finalUrl == null) {
            return null;
        }
        String volume = System.getenv("DOCKER_VOLUME");
        if (volume == null) {
            volume = "";
        }
        String outputFilePath = volume + repoToBeAnalysed.getRepoName() + ".zip"; // The local file where the repository will be saved
        File file = new File(outputFilePath);

        Request.Builder builder = new Request.Builder();
        builder.url(finalUrl);
        builder.get();
        if (token != null) {
            builder.addHeader("Authorization", "Bearer " + token);
        }
        Request request = builder.build();
        Response response = null;
        try {
            response = client.newCall(request).execute();
            if (response.isSuccessful() && response.body() != null) {
                // Get input stream from the response body
                InputStream inputStream = Objects.requireNonNull(response.body()).byteStream();

                // Write the input stream to a file
                FileOutputStream fileOutputStream = new FileOutputStream(file);
                try {
                    byte[] buffer = new byte[1024];
                    int bytesRead;
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        fileOutputStream.write(buffer, 0, bytesRead);
                    }
                    loggerMaker.infoAndAddToDb("Repository downloaded successfully.");
                } catch (Exception e) {
                    loggerMaker.errorAndAddToDb("Error while writing the response into file " + e.getMessage());
                    return null;
                } finally {
                    fileOutputStream.flush();
                    fileOutputStream.close();
                }
            } else {
                return null;
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error while downloading zip file " + e.getMessage());
            return null;
        } finally{
            if (response != null) {
                response.close();
            }
        }
        return file.getAbsolutePath();


    }

    private void syncRepoToDashboard(String body, CodeAnalysisRepo repo) {
        try {
            BasicDBObject requestBody = BasicDBObject.parse(body);
            BasicDBList basicDBList = (BasicDBList) requestBody.get("codeAnalysisApisList");
            if (basicDBList.isEmpty()) {
                dataActor.updateRepoLastRun(repo);// No apis found skipping next runs
            }
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

    public void fetchEndpointsUsingAnalyser() {
        if (StringUtils.isEmpty(getFullUrl(""))) {
            loggerMaker.infoAndAddToDb("No source-code-analyser url found, skipping");
            return;
        }
        String repositoryPath = downloadRepository();
        if (repositoryPath == null && this instanceof GithubRepo) {
            //check for main branch instead of master
            ((GithubRepo) this).setCheckForMain(true);
            repositoryPath = downloadRepository();
        }
        BasicDBObject requestBody = getCodeAnalysisBody(repositoryPath);
        if (requestBody == null) {
            loggerMaker.infoAndAddToDb("No request body found, skipping");
            return;
        }

        OriginalHttpRequest originalHttpRequest = new OriginalHttpRequest(getFullUrl("/api/run-analyser"), "", "POST", requestBody.toJson(), null, "");
        try {
            OriginalHttpResponse originalHttpResponse = ApiExecutor.sendRequest(originalHttpRequest, true, null, false, null, true);
            String responsePayload = originalHttpResponse.getBody();
            loggerMaker.infoAndAddToDb(responsePayload);
            if (originalHttpResponse.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("non 2xx response in fetching apis api/run-analyser from source code", LoggerMaker.LogDb.RUNTIME);
                return;
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error while fetching api's from code-analysis for repository:" + repoToBeAnalysed);
        }

        /*
         * path parameter from the request body is used
         * to check the sanctity of the code analyser module
         */
        originalHttpRequest = new OriginalHttpRequest(getFullUrl("/api/check-analyser"), "", "POST", requestBody.toJson(), null, "");

        final int DELAY = 10 * 1000; // 10 seconds
        int MAX_TIMEOUT_CHECKS = (30 * 60 * 60) / 10;
        try {
            int timeout_minutes = Integer.parseInt(System.getenv("CODE_ANALYSIS_TIMEOUT"));
            if (timeout_minutes > 0) {
                MAX_TIMEOUT_CHECKS = (timeout_minutes * 60 * 60) / 10;
            }
        } catch (Exception e) {

        }
        int counter = 0;

        while (true) {
            try {
                counter++;
                if (counter > MAX_TIMEOUT_CHECKS) {
                    dataActor.updateRepoLastRun(repoToBeAnalysed);
                    break;
                }
                Thread.sleep(DELAY);
                OriginalHttpResponse originalHttpResponse = ApiExecutor.sendRequest(originalHttpRequest, true, null,
                        false, null, true);
                String responsePayload = originalHttpResponse.getBody();
                loggerMaker.infoAndAddToDb(responsePayload);
                if ((originalHttpResponse.getStatusCode() < 200 || originalHttpResponse.getStatusCode() > 300)
                        || responsePayload == null) {
                    loggerMaker.errorAndAddToDb("non 2xx response in fetching apis api/check-analyser from source code",
                            LoggerMaker.LogDb.RUNTIME);
                    return;
                }

                BasicDBObject responseBody = BasicDBObject.parse(responsePayload);
                String status = (String) responseBody.getOrDefault("status", "not found");
                String error = (String) responseBody.get("error");

                if(error!=null && error.equals("project not found")){
                    loggerMaker.errorAndAddToDb("code-analysis module unable to find project, exiting checking");
                    break;
                }

                if (status.equals("completed")) {
                    syncRepoToDashboard(responsePayload, repoToBeAnalysed);
                    break;
                }
                continue;
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(
                        "Error while fetching api's from code-analysis for repository:" + repoToBeAnalysed);
                break;
            }
        }
        if (repositoryPath != null) {
            File file = new File(repositoryPath);
            if(file.delete()) {
                loggerMaker.infoAndAddToDb("successfully deleted the zip file", LoggerMaker.LogDb.RUNTIME);
            }
        }
    }

    public SourceCodeAnalyserRepo(CodeAnalysisRepo repo) {
        this.repoToBeAnalysed = repo;
    }

    public CodeAnalysisRepo getRepoToBeAnalysed() {
        return repoToBeAnalysed;
    }

    public void setRepoToBeAnalysed(CodeAnalysisRepo repoToBeAnalysed) {
        this.repoToBeAnalysed = repoToBeAnalysed;
    }

    public boolean isAktoGPTEnabled() {
        return isAktoGPTEnabled;
    }

    public void setAktoGPTEnabled(boolean aktoGPTEnabled) {
        isAktoGPTEnabled = aktoGPTEnabled;
    }
}
