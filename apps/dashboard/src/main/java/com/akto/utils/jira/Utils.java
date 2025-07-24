package com.akto.utils.jira;

import com.akto.calendar.DateUtils;
import com.akto.dao.JiraIntegrationDao;
import com.akto.dto.jira_integration.JiraIntegration;
import com.akto.dto.test_editor.YamlTemplate;
import com.akto.dto.test_run_findings.TestingRunIssues;
import com.akto.dto.testing.Remediation;
import com.akto.log.LoggerMaker;
import com.akto.util.Pair;
import com.akto.util.enums.GlobalEnums.TestRunIssueStatus;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import okhttp3.Request;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

public class Utils {

    private static final LoggerMaker loggerMaker = new LoggerMaker(Utils.class, LoggerMaker.LogDb.DASHBOARD);

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
            try {
                Object fieldsObj = additionalIssueFields.get("mandatoryCreateJiraIssueFields");
                if (fieldsObj != null && fieldsObj instanceof List) {
                    List<?> mandatoryCreateJiraIssueFields = (List<?>) fieldsObj;
                    for (Object fieldObj : mandatoryCreateJiraIssueFields) {
                        if (fieldObj instanceof Map<?, ?>) {
                            Map<?, ?> mandatoryField = (Map<?, ?>) fieldObj;
                            Object fieldName = mandatoryField.get("fieldId");
                            if (fieldName == null || !(fieldName instanceof String)) {
                                continue;
                            }
                            String fieldNameStr = (String) fieldName;
                            Object fieldValue = mandatoryField.get("fieldValue");
                            fields.put(fieldNameStr, fieldValue);
                        }
                    }
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return fields;
    }

    public static List<BasicDBObject> buildAdditionalIssueFieldsForJira(YamlTemplate yamlTemplate,
        TestingRunIssues issue, Remediation remediation) {
        List<BasicDBObject> contentList = new ArrayList<>();
        if (yamlTemplate != null) {
            try {
                contentList.add(addHeading(3, "Overview"));
                contentList.add(addHeading(4, "Severity"));
                contentList.add(addParagraph(issue.getSeverity().name()));

                if (yamlTemplate.getInfo() != null) {
                    contentList.add(addHeading(4, "Impact"));
                    contentList.add(addParagraph(yamlTemplate.getInfo().getImpact()));
                    if (CollectionUtils.isNotEmpty(yamlTemplate.getInfo().getTags())) {
                        contentList.add(addHeading(4, "Tags"));
                        contentList.add(addList(yamlTemplate.getInfo().getTags()));
                    }
                    if (yamlTemplate.getInfo().getCompliance() != null && !yamlTemplate.getInfo().getCompliance().getMapComplianceToListClauses().isEmpty()) {
                        contentList.add(addHeading(4, "Compliance"));
                        Map<String, List<String>> complianceMap = yamlTemplate.getInfo().getCompliance()
                            .getMapComplianceToListClauses();
                        for (Map.Entry<String, List<String>> entry : complianceMap.entrySet()) {
                            String complianceName = entry.getKey();
                            List<String> clauses = entry.getValue();
                            contentList.add(addHeading(5, complianceName));
                            if (CollectionUtils.isNotEmpty(clauses)) {
                                contentList.add(addList(clauses));
                            } else {
                                contentList.add(addParagraph("No clauses available."));
                            }
                        }
                    }

                    if (CollectionUtils.isNotEmpty(yamlTemplate.getInfo().getCwe())) {
                        contentList.add(addHeading(4, "CWE"));
                        contentList.add(addList(yamlTemplate.getInfo().getCwe()));
                    }
                    if (CollectionUtils.isNotEmpty(yamlTemplate.getInfo().getCve())) {
                        contentList.add(addHeading(4, "CVE"));
                        contentList.add(addList(yamlTemplate.getInfo().getCve()));
                    }
                    if (CollectionUtils.isNotEmpty(yamlTemplate.getInfo().getReferences())) {
                        contentList.add(addHeading(4, "References"));
                        contentList.add(addList(yamlTemplate.getInfo().getReferences()));
                    }
                    String remediationText = yamlTemplate.getInfo().getRemediation();
                    if (StringUtils.isBlank(remediationText)) {
                        remediationText =
                            remediation != null ? remediation.getRemediationText() : "No remediation provided.";
                    }
                    contentList.add(addHeading(3, "Remediation"));
                    contentList.add(addParagraph(remediationText));
                }
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e,
                    "Error while adding additional issue details in Jira Payload: " + e.getMessage(),
                    LoggerMaker.LogDb.DASHBOARD);
            }
        }
        contentList.add(addHeading(3, "Timelines (UTC)"));
        contentList.add(getIssueTimelines(issue));
        return contentList;
    }

    private static BasicDBObject getIssueTimelines(TestingRunIssues issue) {
        List<String> timelines = new ArrayList<>();

        timelines.add("Issue Found on " + DateUtils.convertToUtcLocaleDate(issue.getCreationTime() * 1000L));
        if (issue.getTestRunIssueStatus() == TestRunIssueStatus.IGNORED) {
            timelines.add(
                "Issue marked as IGNORED on " + DateUtils.convertToUtcLocaleDate(issue.getLastUpdated() * 1000L)
                    + ". Reason: " + StringUtils.defaultIfBlank(issue.getIgnoreReason(), "No reason provided."));
        }
        if (issue.getTestRunIssueStatus() == TestRunIssueStatus.FIXED) {
            timelines.add(
                "Issue marked as FIXED on " + DateUtils.convertToUtcLocaleDate(issue.getLastUpdated() * 1000L));
        }
        return addList(timelines);
    }

    private static BasicDBObject addHeading(int level, String text) {
        return new BasicDBObject("type", "heading")
            .append("attrs", new BasicDBObject("level", level))
            .append("content", Collections.singletonList(new BasicDBObject("type", "text").append("text", text)));
    }

    private static BasicDBObject addParagraph(String text) {
        return new BasicDBObject("type", "paragraph")
            .append("content", Collections.singletonList(new BasicDBObject("type", "text").append("text", text)));
    }

    private static BasicDBObject addList(List<String> items) {
        BasicDBList listItems = new BasicDBList();
        for (String item : items) {
            listItems.add(addListItem(item));
        }
        return new BasicDBObject("type", "bulletList")
            .append("content", listItems);
    }

    private static BasicDBObject addListItem(String text) {
        return new BasicDBObject("type", "listItem")
            .append("content", Collections.singletonList(
                new BasicDBObject("type", "paragraph")
                    .append("content",
                        Collections.singletonList(new BasicDBObject("type", "text").append("text", text)))
            ));
    }
}