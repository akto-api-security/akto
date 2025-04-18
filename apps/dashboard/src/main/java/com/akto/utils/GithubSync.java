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

import com.akto.github.GithubFile;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.http_util.CoreHTTPClient;

public class GithubSync {
    private static final LoggerMaker loggerMaker = new LoggerMaker(GithubSync.class, LogDb.DASHBOARD);;
    private static final OkHttpClient client = CoreHTTPClient.client.newBuilder().build();

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

            if (fileResponse.code() != 200) {
                loggerMaker.errorAndAddToDb(String.format("Unable to retrieve file %s from repo %s", fileName, repo), LogDb.DASHBOARD);
            } else {
                ResponseBody fileResponseBody = fileResponse.body();
                String fileContents = fileResponseBody.string();
                
                githubFile = new GithubFile(fileName, filePath, fileContents, latestSha);
            }
        } catch (IOException ex) {
            loggerMaker.errorAndAddToDb(ex, String.format("Error while syncing file %s from repo %s", filePath, repo), LogDb.DASHBOARD);
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

            if (dirResponse.code() != 200) {
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
            loggerMaker.errorAndAddToDb(ex,String.format("Error while syncing files from directory %s in Github repo %s %s ", repo, dirPath, ex.toString()), LogDb.DASHBOARD);
            return null;
        }
        
        return dirContents;
    }

    public byte[] syncRepo(String repo, String branch) {

        String url = String.format("https://github.com/%s/archive/refs/heads/%s.zip", repo, branch);

        return syncRepo(url);
    }

    private static final long REPO_SIZE_LIMIT = 1024*1024*10; // 10 MB

    public byte[] syncRepo(String url) {
        byte[] repoZip = null;

        Request request = new Request.Builder()
                .url(url)
                /*
                 * this header is needed to force the URL 
                 * to send the content-length header.
                 */
                .addHeader("Accept-encoding", "None")
                .build();
        Response response = null;

        try {
            response =  client.newCall(request).execute();

            if (response.isSuccessful()) {
                long content_length = 0;
                String content_length_header = response.header("content-length");
                if (content_length_header != null) {
                    content_length = Long.parseLong(content_length_header);
                }
                if (content_length > REPO_SIZE_LIMIT) {
                    throw new Exception("Repo size is too large, max allowed size is 10 MB");
                }

                loggerMaker.debugAndAddToDb(String.format("Downloaded github repo archive: %s", url), LogDb.DASHBOARD);
                repoZip = response.body().bytes();
            } else {
                loggerMaker.errorAndAddToDb(String.format("Failed to download the zip archive from url %s. Status code: %d", url, response.code()), LogDb.DASHBOARD);
            }
        } catch (Exception ex) {
            loggerMaker.errorAndAddToDb(ex, String.format("Failed to download the zip archive from url %s. Error %s", url, ex.getMessage()), LogDb.DASHBOARD);
        } 
        finally {
            if (response != null) {
                response.close();
            }
        }

        return repoZip;
    }
}
