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
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;

public class GithubSync {
    private static final Logger logger = LoggerFactory.getLogger(GithubSync.class);
    private static final LoggerMaker loggerMaker = new LoggerMaker(GithubSync.class);
    private static final OkHttpClient client = new OkHttpClient();

    public Map<String, String> syncDir(String repo, String dirPath) {
        Map<String, String> dirContents = new HashMap<String, String>();

        // Get Github repo tree - Eg: https://api.github.com/repos/akto-api-security/akto/git/trees/master?recursive=1
        Request treeRequest = new Request.Builder()
                .url(String.format("https://api.github.com/repos/%s/git/trees/master?recursive=1", repo))
                .build();

        try {
            Response treeResponse = client.newCall(treeRequest).execute();
            ResponseBody treeResponseBody = treeResponse.body();

            if (treeResponseBody != null) {
                String json = treeResponseBody.string();
                JSONObject jsonObject = new JSONObject(json);
                JSONArray tree = jsonObject.getJSONArray("tree");
                for (Object treeNodeObject : tree) {
                    JSONObject treeNode = (JSONObject) treeNodeObject;
                    String path = treeNode.getString("path");
                    
                    // Retreive Github file contents 
                    if(path.contains(dirPath)) {
                        Request fileRequest = new Request.Builder()
                            .url(String.format("https://raw.githubusercontent.com/%s/master/%s", repo, path))
                            .build();
                        Response fileResponse = client.newCall(fileRequest).execute();
                        ResponseBody fileResponseBody = fileResponse.body();
                        String fileContents = fileResponseBody.string();
                    
                        String[] pathSplit = path.split("/");
                        String filename = pathSplit[pathSplit.length - 1];
                        
                        dirContents.put(filename, fileContents);
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
