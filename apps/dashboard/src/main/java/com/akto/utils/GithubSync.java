package com.akto.utils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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

    public List<String> getDirFileNames(String repo, String dirPath) {
        List<String> dirFileNames = new ArrayList<>();
        
        //String dirRequestUrl = String.format("https://api.github.com/repos/%s/contents/%s", repo, dirPath);
        String dirRequestUrl = "https://api.github.com/repositories/595526110/contents/apps/dashboard/src/main/resources/inbuilt_test_yaml_files?ref=test/test-editor-files-versioning-isactive";

        Request dirRequest = new Request.Builder()
                .url(dirRequestUrl)
                .build();


        try {
            Response dirResponse = client.newCall(dirRequest).execute();

            if (dirResponse.code() == 404) {
                loggerMaker.errorAndAddToDb(String.format("Could not retrieve directory file names %s of repo %s", dirPath, repo), LogDb.DASHBOARD);
                return null;
            }

            ResponseBody dirResponseBody = dirResponse.body();
            if (dirResponseBody != null) {
                String jsonString = dirResponseBody.string();
                JSONArray dirContentsArray = new JSONArray(jsonString);
                for (Object file : dirContentsArray) {
                    JSONObject fileObject = (JSONObject) file;
                    String fileName = fileObject.getString("name");
                    dirFileNames.add(fileName);
                }
            }
        } catch (Exception ex) {
            loggerMaker.errorAndAddToDb(String.format("Error while syncing files from directory %s in Github repo %s %s ", repo, dirPath, ex.toString()), LogDb.DASHBOARD);
            return null;
        }
        
        return dirFileNames;
    }

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

        //String fileRequestUrl = String.format("https://raw.githubusercontent.com/%s/master/%s", repo, filePath);
        String fileRequestUrl = String.format("https://raw.githubusercontent.com/%s/test/test-editor-files-versioning-isactive/%s", repo, filePath);

        Request fileRequest = new Request.Builder()
                            .url(fileRequestUrl)
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
        
        //String dirRequestUrl = String.format("https://api.github.com/repos/%s/contents/%s", repo, dirPath);
        String dirRequestUrl = "https://api.github.com/repositories/595526110/contents/apps/dashboard/src/main/resources/inbuilt_test_yaml_files?ref=test/test-editor-files-versioning-isactive";

        Request dirRequest = new Request.Builder()
                .url(dirRequestUrl)
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

                    // Files which are not updated have null values
                    dirContents.put(fileName, githubFile);
                    
                }
            }
        } catch (Exception ex) {
            loggerMaker.errorAndAddToDb(String.format("Error while syncing files from directory %s in Github repo %s %s ", repo, dirPath, ex.toString()), LogDb.DASHBOARD);
            return null;
        }
        
        return dirContents;
    }


}
