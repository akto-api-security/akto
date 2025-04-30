package com.akto;

import com.akto.dto.CodeAnalysisRepo;
import com.mongodb.BasicDBObject;
import org.apache.commons.lang3.StringUtils;

public class GithubRepo extends SourceCodeAnalyserRepo{

    private static final String GITHUB_URL = "https://api.github.com/repos/__ORGANISATION__/__REPOSITORY__/zipball/master";
    private static final String GITHUB_URL_MAIN = "https://api.github.com/repos/__ORGANISATION__/__REPOSITORY__/zipball/main";
    private static final String GITHUB_ACCESS_TOKEN = System.getenv("GITHUB_ACCESS_TOKEN");
    private boolean checkForMain = false;
    public void setCheckForMain(boolean checkForMain) {
        this.checkForMain = checkForMain;
    }
    public GithubRepo(CodeAnalysisRepo repo) {
        super(repo);
    }

    @Override
    public String getToken() {
        return GITHUB_ACCESS_TOKEN;
    }

    @Override
    public String getRepoUrl() {
        if (checkForMain) {
            return GITHUB_URL_MAIN.replace("__ORGANISATION__", this.getRepoToBeAnalysed().getProjectName())
                    .replace("__REPOSITORY__", this.getRepoToBeAnalysed().getRepoName());
        }
        return GITHUB_URL.replace("__ORGANISATION__", this.getRepoToBeAnalysed().getProjectName())
                .replace("__REPOSITORY__", this.getRepoToBeAnalysed().getRepoName());
    }

    @Override
    public BasicDBObject getCodeAnalysisBody(String path) {
        if (path == null || StringUtils.isEmpty(GITHUB_ACCESS_TOKEN)) {
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
        if (StringUtils.isEmpty(GITHUB_ACCESS_TOKEN)) {
            return false;
        }
        return true;
    }
}
