package com.akto;

import com.akto.testing.HTTPClientHandler;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Objects;

public class Main {
    public static final String JSON_CONTENT_TYPE = "application/json";

    public static final String BITBUCKET_URL = "http://localhost:7990";

    private String listAllProjectsUrl(String hostUrl) {
        return hostUrl + "/rest/api/1.0/projects";
    }

    public static void main(String[] args) {

        String bitbucketUrl = "http://localhost:7990/projects/SPRIN";
        String bitbucketToken = "BBDC-NjI4NTM0OTA0MjQ5Om7JQ1F5TZ9lR9gKAfaDCdeKyLzY";

        String repoUrl = "http://localhost:7990/rest/api/1.0/projects/SPRIN/repos/spring-boot-tutorial-master/archive"; // Replace with your repo URL
        String outputFilePath = "repository.zip"; // The local file where the repository will be saved
        File file = new File(outputFilePath);

        Request.Builder builder = new Request.Builder();
        builder.url(repoUrl);
        builder.get();
        builder.addHeader("Authorization", "Bearer " + bitbucketToken);
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
                }
            } else {
                System.out.println("Failed to download repository. Response code: " + response.code());
            }
        } catch (Exception e) {
            System.out.println("failed to call" + e.getMessage());
        }
    }
}