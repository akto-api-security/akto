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

    public String syncFile(String download_url) {
        String fileContents = null; 

        Request fileRequest = new Request.Builder()
                            .url(download_url)
                            .build();
                            
        try {
            Response fileResponse = client.newCall(fileRequest).execute();
            ResponseBody fileResponseBody = fileResponse.body();
            fileContents = fileResponseBody.string();
        } catch (IOException ex) {
            loggerMaker.errorAndAddToDb(String.format("Error while syncing file %s ", download_url), LogDb.DASHBOARD);
        }
        
        return fileContents;
    }

    public Map<String, GithubFile> syncDir(String repo, String dirPath, Map<String, String> fileShaCheck) {
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
                    String filename = fileObject.getString("name");
                    String filepath = fileObject.getString("path");
                    String download_url = fileObject.getString("download_url");
                    String sha = fileObject.getString("sha");

                    //check if file has not been updated
                    if (fileShaCheck != null) {
                        if (fileShaCheck.containsKey(filename)) {
                            if (fileShaCheck.get(filename).equals(sha)){
                                //skip file
                                continue;
                            }
                        }
                    }
                    
                    // Retreive Github file contents 
                    String fileContents = syncFile(download_url);
                    GithubFile githubFile = new GithubFile(filename, filepath, fileContents, sha);
                    dirContents.put(filename, githubFile);
                }
            }
        } catch (Exception ex) {
            loggerMaker.errorAndAddToDb(String.format("Error while syncing files from directory %s in Github repo %s %s ", repo, dirPath, ex.toString()), LogDb.DASHBOARD);
            return null;
        }
        
        return dirContents;
    }
}
