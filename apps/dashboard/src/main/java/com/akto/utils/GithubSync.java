package com.akto.utils;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.akto.github.GithubFile;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;

public class GithubSync {
    private static final Logger logger = LoggerFactory.getLogger(GithubSync.class);
    private static final LoggerMaker loggerMaker = new LoggerMaker(GithubSync.class);
    private static final OkHttpClient client = new OkHttpClient();

    public GithubFile syncFile(String repo, String filePath, String latestSha, Map<String, String> githubFileShaMap) {
        String[] filePathSplit = filePath.split("/");
        String fileName = filePathSplit[filePathSplit.length - 1];
        
        //check if file has not been updated
        if (latestSha != null && githubFileShaMap != null) {
            if (githubFileShaMap.containsKey(fileName)) {
                if (githubFileShaMap.get(fileName).equals(latestSha)){
                    //skip file
                    return null;
                }
            }
        }

        GithubFile githubFile = null;

        Request fileRequest = new Request.Builder()
                            .url(String.format("https://raw.githubusercontent.com/%s/master/%s", repo, filePath))
                            .build();
                            
        try {
            Response fileResponse = client.newCall(fileRequest).execute();

            if (fileResponse.code() == 404) {
                loggerMaker.errorAndAddToDb(String.format("File %s not found in repo %s", fileName, repo), LogDb.DASHBOARD);
            } else {
                ResponseBody fileResponseBody = fileResponse.body();
                String fileContents = fileResponseBody.string();
                
                githubFile = new GithubFile(fileName, filePath, fileContents, latestSha);
            }
        } catch (IOException ex) {
            loggerMaker.errorAndAddToDb(String.format("Error while syncing file %s from repo %s", filePath, repo), LogDb.DASHBOARD);
        }

        return githubFile;
    }

    public Map<String, GithubFile> syncDir(String repo, String dirPath, Map<String, String> githubFileShaMap) {
        Map<String, GithubFile> dirContents = new HashMap<>();
        
        Request dirRequest = new Request.Builder()
                .url(String.format("https://api.github.com/repos/%s/contents/%s", repo, dirPath))
                .build();

        try {
            Response dirResponse = client.newCall(dirRequest).execute();

            if (dirResponse.code() == 404) {
                loggerMaker.errorAndAddToDb(String.format("Could not retrieve directory contents %s of repo %s", dirPath, repo), LogDb.DASHBOARD);
                return null;
            }

            ResponseBody dirResponseBody = dirResponse.body();
            if (dirResponseBody != null) {
                String jsonString = dirResponseBody.string();
                JSONArray dirContentsArray = new JSONArray(jsonString);
                for (Object file : dirContentsArray) {
                    JSONObject fileObject = (JSONObject) file;
                    String fileName = fileObject.getString("name");
                    String filePath = fileObject.getString("path");
                    String latestSha = fileObject.getString("sha");
                    
                    // Retreive Github file 
                    GithubFile githubFile = syncFile(repo, filePath, latestSha, githubFileShaMap);

                    if (githubFile != null) {
                        dirContents.put(fileName, githubFile);
                    }
                }
            }
        } catch (Exception ex) {
            loggerMaker.errorAndAddToDb(String.format("Error while syncing files from directory %s in Github repo %s %s ", repo, dirPath, ex.toString()), LogDb.DASHBOARD);
            return null;
        }
        
        return dirContents;
    }
}
