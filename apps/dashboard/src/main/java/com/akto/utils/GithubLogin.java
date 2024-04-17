package com.akto.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.akto.dao.ConfigsDao;
import com.akto.dao.context.Context;
import com.akto.dto.Config;
import com.akto.dto.Config.GithubConfig;
import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.OriginalHttpResponse;
import com.akto.testing.ApiExecutor;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public class GithubLogin {

    public static final int PROBE_PERIOD_IN_SECS = 60;
    private static GithubLogin instance = null;
    private GithubConfig githubConfig = null;
    private int lastProbeTs = 0;

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

    public static String getAuthorisationUrl() {
        if (getInstance() == null) return null;

        GithubConfig ghConfig = getInstance().getGithubConfig();
        if (ghConfig == null) return null;

        String authUrl = "https://github.com/login/oauth/authorize?scope=user,user:email&client_id=" + ghConfig.getClientId();
        return authUrl;
    }

    public static List<Map<String, String>> getEmailRequest(String emailsUrl, String accessToken){
        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("Content-Type", Collections.singletonList("application/json"));
        headers.put("Authorization", Collections.singletonList("Bearer " + accessToken));
        
        OriginalHttpRequest request = new OriginalHttpRequest(emailsUrl, "", "GET", null, headers, "");
        OriginalHttpResponse response = null;
        try {
            response = ApiExecutor.sendRequest(request, false, null, false, new ArrayList<>());
            return objectMapper.readValue(response.getBody(), new TypeReference<List<Map<String, String>>>() {});
        }catch(Exception e){
            return null;
        }
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
