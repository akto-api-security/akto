package com.akto.utils;

import com.akto.dao.ConfigsDao;
import com.akto.dao.context.Context;
import com.akto.dto.Config;
import com.akto.dto.Config.GithubConfig;
import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.OriginalHttpResponse;
import com.akto.testing.ApiExecutor;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;

public class GithubLogin {

    public static final int PROBE_PERIOD_IN_SECS = 60;
    private static GithubLogin instance = null;
    private GithubConfig githubConfig = null;
    private int lastProbeTs = 0;
    public static final String GET_GITHUB_EMAILS_URL = "https://api.github.com/user/emails";

    public static GithubLogin getInstance() {
        boolean shouldProbeAgain = true;
        if (instance != null) {
            shouldProbeAgain = Context.now() - instance.lastProbeTs >= PROBE_PERIOD_IN_SECS;
        }

        if (shouldProbeAgain) {
            GithubConfig ghConfig = (Config.GithubConfig) ConfigsDao.instance.findOne("_id", "GITHUB-ankush");
            if (instance == null) {
                instance = new GithubLogin();
            }

            instance.githubConfig = ghConfig;
            instance.lastProbeTs = Context.now();
        }

        return instance;
    }

    public static String getClientId() {
        if (getInstance() == null) return null;

        GithubConfig ghConfig = getInstance().getGithubConfig();
        if (ghConfig == null) return null;

        return ghConfig.getClientId();
    }

    public static String getGithubUrl() {
        if (getInstance() == null) return null;

        GithubConfig ghConfig = getInstance().getGithubConfig();
        if (ghConfig == null) return null;

        String githubUrl = ghConfig.getGithubUrl();
        if (githubUrl == null) return null;
        if (githubUrl.endsWith("/")) githubUrl = githubUrl.substring(0, githubUrl.length() - 1);
        return githubUrl;
    }

    public static List<Map<String, String>> getEmailRequest(String accessToken){
        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("Content-Type", Collections.singletonList("application/vnd.github+json"));
        headers.put("Authorization", Collections.singletonList("Bearer " + accessToken));
        headers.put("X-GitHub-Api-Version", Collections.singletonList("2022-11-28"));

        OriginalHttpRequest request = new OriginalHttpRequest(GET_GITHUB_EMAILS_URL, "", "GET", null, headers, "");
        OriginalHttpResponse response = null;
        try {
            response = ApiExecutor.sendRequest(request, false, null, false, new ArrayList<>());
            return objectMapper.readValue(response.getBody(), new TypeReference<List<Map<String, String>>>() {});
        }catch(Exception e){
            return null;
        }
    }

    public static String getPrimaryGithubEmail(List<Map<String, String>> emailResp){
        if(emailResp == null){
            return  "";
        }else{
            for (Map<String, String> entryMap : emailResp) {
                if(entryMap.get("primary").equals("true")){
                    return entryMap.get("email");
                }
            }
        }
        return null;
    }

    private GithubLogin() {
    }

    public GithubConfig getGithubConfig() {
        return this.githubConfig;
    }

    public int getLastProbeTs() {
        return lastProbeTs;
    }

}
