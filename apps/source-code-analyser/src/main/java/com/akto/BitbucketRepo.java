package com.akto;

import com.akto.dto.CodeAnalysisRepo;
import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.OriginalHttpResponse;
import com.akto.log.LoggerMaker;
import com.akto.testing.ApiExecutor;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import de.flapdoodle.embed.process.runtime.NUMA;
import org.apache.kafka.common.protocol.types.Field;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BitbucketRepo extends SourceCodeAnalyserRepo{

    private static final String BITBUCKET_URL = System.getenv("BITBUCKET_HOST");
    private static final String BITBUCKET_TOKEN = System.getenv("BITBUCKET_TOKEN");
    private static final String REPO_URL = "/rest/api/1.0/projects/__PROJECT_KEY__/repos/__REPO_NAME__/archive";
    private static final LoggerMaker loggerMaker = new LoggerMaker(BitbucketRepo.class, LoggerMaker.LogDb.RUNTIME);

    private static final Map<String, String> allProjectKeys = new HashMap<>();
    public BitbucketRepo(CodeAnalysisRepo repo) {
        super(repo);
    }

    @Override
    public String getToken() {
        return BITBUCKET_TOKEN;
    }

    private static Map<String, List<String>> buildHeaders(String token) {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("Authorization", Collections.singletonList("Bearer " + BITBUCKET_TOKEN));
        return headers;
    }

    public static void fetchAllProjectKeys() {
        allProjectKeys.clear();
        if (BITBUCKET_URL == null || BITBUCKET_TOKEN == null) {
            return;
        }
        Map<String, List<String>> headers = buildHeaders(BITBUCKET_TOKEN);
        String url = BITBUCKET_URL + "/rest/api/1.0/projects";
        OriginalHttpRequest request = new OriginalHttpRequest(url, "", "GET", null, headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null, true);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("non 2xx response in fetchAllProjectKeys");
                return;
            }
            BasicDBObject payloadObj;
            try {
                payloadObj =  BasicDBObject.parse(responsePayload);
                BasicDBList values = (BasicDBList) payloadObj.get("values");
                values.forEach(projectDetailObj -> {
                    BasicDBObject projectDetails = (BasicDBObject) projectDetailObj;
                    allProjectKeys.put(projectDetails.getString("name"), projectDetails.getString("key"));
                });
            } catch(Exception e) {
                loggerMaker.errorAndAddToDb("failed while parsing response for fetchAllProjectKeys");
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in fetchEstimatedDocCount" + e);
        }
    }

    @Override
    public String getRepoUrl() {
        if (BITBUCKET_URL == null || allProjectKeys.isEmpty()) {
            return null;
        }
        String url = BITBUCKET_URL + REPO_URL;
        return url.replace("__PROJECT_KEY__", allProjectKeys.get(this.getRepoToBeAnalysed().getProjectName()))
                .replace("__REPO_NAME__",this.getRepoToBeAnalysed().getRepoName());
    }

    @Override
    public BasicDBObject getCodeAnalysisBody(String path) {
        if (path == null || allProjectKeys.isEmpty() || BITBUCKET_URL == null || BITBUCKET_TOKEN == null) {
            return null;
        }

        BasicDBObject requestBody = new BasicDBObject();
        requestBody.put("path", path);
        requestBody.put("projectKey",allProjectKeys.get(this.getRepoToBeAnalysed().getProjectName()));
        requestBody.put("repoName",this.getRepoToBeAnalysed().getRepoName());
        requestBody.put("bitbucketHost",BITBUCKET_URL);
        requestBody.put("is_bitbucket",true);
        return requestBody;
    }

    public static boolean doesEnvVariablesExists() {
        if (BITBUCKET_TOKEN == null || BITBUCKET_URL == null) {
            return false;
        }
        return true;
    }

}
