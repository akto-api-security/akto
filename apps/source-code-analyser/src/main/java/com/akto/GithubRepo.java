package com.akto;

import com.akto.dto.CodeAnalysisRepo;
import com.mongodb.BasicDBObject;

public class GithubRepo extends SourceCodeAnalyserRepo{

    private static final String GITHUB_URL = "https://api.github.com/repos/__ORGANISATION__/__REPOSITORY__/zipball/master";
    private static final String GITHUB_ACCESS_TOKEN = System.getenv("GITHUB_ACCESS_TOKEN");

    public GithubRepo(CodeAnalysisRepo repo) {
        super(repo);
    }

    @Override
    public String getToken() {
        return GITHUB_ACCESS_TOKEN;
    }

    @Override
    public String getRepoUrl() {
        String finalUrl = GITHUB_URL.replace("__ORGANISATION__", this.getRepoToBeAnalysed().getProjectName())
                .replace("__REPOSITORY__", this.getRepoToBeAnalysed().getRepoName());
        return finalUrl;
    }

    @Override
    public BasicDBObject getCodeAnalysisBody(String path) {
        if (path == null || GITHUB_ACCESS_TOKEN == null) {
            return null;
        }

        BasicDBObject requestBody = new BasicDBObject();
        requestBody.put("path", path);
        requestBody.put("orgName",this.getRepoToBeAnalysed().getProjectName());
        requestBody.put("repoName",this.getRepoToBeAnalysed().getRepoName());
        requestBody.put("is_github",true);
        return requestBody;
    }

    public static boolean doesEnvVariablesExists() {
        if (GITHUB_ACCESS_TOKEN == null) {
            return false;
        }
        return true;
    }
}
