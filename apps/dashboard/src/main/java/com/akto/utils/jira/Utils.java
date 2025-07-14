package com.akto.utils.jira;

import java.util.Base64;

import org.apache.commons.lang3.StringUtils;

import com.akto.dao.JiraIntegrationDao;
import com.akto.dto.jira_integration.JiraIntegration;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;

import okhttp3.Request;

public class Utils {
    public static String buildApiToken(String apiKey){
        if (StringUtils.isEmpty(apiKey)) {
            return null;
        }
        if (apiKey.contains("******")) {
            JiraIntegration jiraIntegration = JiraIntegrationDao.instance.findOne(Filters.empty(), Projections.include(JiraIntegration.API_TOKEN));
            if (jiraIntegration != null) {
                return jiraIntegration.getApiToken();
            }
        }
        return apiKey;
    }

    public static Request.Builder buildBasicRequest(String url, String userEmail, String apiToken, boolean isGzipEnabled) {
        String authHeader = Base64.getEncoder().encodeToString((userEmail + ":" + apiToken).getBytes());
        Request.Builder builder = new Request.Builder();
        builder.addHeader("Authorization", "Basic " + authHeader);
        if (isGzipEnabled) {
            builder.addHeader("Accept-Encoding", "gzip");
        }else{
            builder.addHeader("Accept", "application/json");
        }
        builder = builder.url(url);
        return builder;
    }

    public static Request retryWithoutGzipRequest(Request.Builder builder, String url) {
        builder.removeHeader("Accept-Encoding");
        builder = builder.url(url);
        return builder.build();
    }
}