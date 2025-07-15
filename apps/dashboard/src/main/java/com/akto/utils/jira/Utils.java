package com.akto.utils.jira;

import java.util.Base64;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import com.akto.dao.JiraIntegrationDao;
import com.akto.dto.jira_integration.JiraIntegration;
import com.akto.util.Pair;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
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

    public static String handleError(String responsePayload){
        if (responsePayload != null) {
            try {
                BasicDBObject obj = BasicDBObject.parse(responsePayload);
                List<String> errorMessages = (List) obj.get("errorMessages");
                String error;
                if (errorMessages.size() == 0) {
                    BasicDBObject errObj = BasicDBObject.parse(obj.getString("errors"));
                    error = errObj.getString("project");
                } else {
                    error = errorMessages.get(0);
                }
                return error;
            } catch (Exception e) {
                return "Error parsing response: " + e.getMessage();
            }
        }
        return null;
    }

    public static Pair<String, String> getJiraTicketUrlPair(String responsePayload, String jiraBaseUrl) {
        if (StringUtils.isEmpty(responsePayload)) {
            return null;
        }
        BasicDBObject obj = BasicDBObject.parse(responsePayload);
        String jiraTicketKey = obj.getString("key");
        return new Pair<>(jiraBaseUrl + "/browse/" + jiraTicketKey, jiraTicketKey);
    }

    public static BasicDBObject buildPayloadForJiraTicket(String summary, String projectKey, String issueType, BasicDBList contentList, Map<String, Object> additionalIssueFields) {
        BasicDBObject fields = new BasicDBObject();
        fields.put("summary", summary);
        fields.put("project", new BasicDBObject("key", projectKey));
        fields.put("issuetype", new BasicDBObject("id", issueType));
        fields.put("description", new BasicDBObject("type", "doc").append("version", 1).append("content", contentList));

        if (additionalIssueFields != null) {
            Object fieldsObj = additionalIssueFields.get("mandatoryCreateJiraIssueFields");

            if (fieldsObj != null && fieldsObj instanceof List) {
                List<?> mandatoryCreateJiraIssueFields = (List<?>) fieldsObj;
                for (Object fieldObj : mandatoryCreateJiraIssueFields) {
                    if (fieldObj instanceof Map<?, ?>) {
                        Map<?, ?> mandatoryField = (Map<?, ?>) fieldObj;
                        String fieldName = (String) mandatoryField.get("fieldId");
                        String fieldValue = (String) mandatoryField.get("fieldValue");
                        // Add to fields object

                        if (fieldName == null) continue;
                        fields.put(fieldName, fieldValue);
                    }
            }
            }
        }
        return fields;
    }
}