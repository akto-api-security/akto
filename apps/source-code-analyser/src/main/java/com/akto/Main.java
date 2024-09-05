package com.akto;

import com.akto.dto.CodeAnalysisRepo;
import com.akto.testing.HTTPClientHandler;
import okhttp3.*;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;


public class Main {
    public static final String JSON_CONTENT_TYPE = "application/json";

    public static final String BITBUCKET_URL = System.getenv("BITBUCKET_HOST");
    public static final String SOURCE_CODE_HOST = System.getenv("SOURCE_CODE_HOST");
    public static final String BITBUCKET_TOKEN = System.getenv("BITBUCKET_TOKEN");
    public static final String ALL_PROJECT_LIST = "ALL_PROJECT_LIST";
    public static final String SOURCE_CODE_URL = "SOURCE_CODE_URL";

    static {
        Map<String, String> allApiEndpoints = new HashMap<>();
        allApiEndpoints.put(ALL_PROJECT_LIST, "/rest/api/1.0/projects");
        allApiEndpoints.put(SOURCE_CODE_URL, "/api/run-analyser");
        allApiEndpoints.put(ALL_PROJECT_LIST, "/rest/api/1.0/projects/SPRIN/repos/spring-boot-tutorial-master/archive");
        allApiEndpoints.put(ALL_PROJECT_LIST, "/rest/api/1.0/projects");
    }

    public static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);


    private static List<CodeAnalysisRepo> fetchReposToSync () {
        List<CodeAnalysisRepo> list = new ArrayList<>();
        return list;
    }

    private static Map<String, String> fetchAllProjectKeys() {
        return null;
    }

    public static void main(String[] args) throws InterruptedException {

        while (true) {
            List<CodeAnalysisRepo> repos = fetchReposToSync();
            String repoUrl = "http://localhost:7990/rest/api/1.0/projects/SPRIN/repos/spring-boot-tutorial-master/archive"; // Replace with your repo URL
            String outputFilePath = "repository.zip"; // The local file where the repository will be saved
            File file = new File(outputFilePath);

            Request.Builder builder = new Request.Builder();
            builder.url(repoUrl);
            builder.get();
            builder.addHeader("Authorization", "Bearer " + BITBUCKET_TOKEN);
            HTTPClientHandler.initHttpClientHandler(false);
            OkHttpClient okHttpClient = HTTPClientHandler.instance.getHTTPClient(false, JSON_CONTENT_TYPE);
            Request request = builder.build();
            try {
                Response response = okHttpClient.newCall(request).execute();
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
                        System.out.println("Repository downloaded successfully.");
                        Request.Builder codeAnalyserRequest = new Request.Builder();
                        codeAnalyserRequest.url(SOURCE_CODE_HOST + SOURCE_CODE_URL);
                        codeAnalyserRequest.method("POST", RequestBody.create("{\"path\":\"" + file.getAbsolutePath() + "\"}", MediaType.parse(JSON_CONTENT_TYPE)));
                        response = okHttpClient.newCall(codeAnalyserRequest.build()).execute();
                        if (response.isSuccessful() && response.body() != null) {
                            ResponseBody responseBody = response.peekBody(1024*1024);
                            System.out.println(responseBody.string());

                        }

                    }
                } else {
                    System.out.println("Failed to download repository. Response code: " + response.code());
                }
            } catch (Exception e) {
                System.out.println("failed to call" + e.getMessage());
            }

            Thread.sleep(1000);
        }

    }
}