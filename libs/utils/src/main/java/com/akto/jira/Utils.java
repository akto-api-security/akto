package com.akto.jira;

import com.akto.calendar.DateUtils;
import com.akto.dao.JiraIntegrationDao;
import com.akto.dto.jira_integration.JiraIntegration;
import com.akto.dto.test_editor.Info;
import com.akto.dto.test_editor.YamlTemplate;
import com.akto.dto.test_run_findings.TestingRunIssues;
import com.akto.dto.testing.ComplianceMapping;
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
import org.apache.commons.collections.MapUtils;
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
        TestingRunIssues issue,
        Remediation remediation) {
        List<BasicDBObject> contentList = new ArrayList<>();

        if (yamlTemplate == null) {
            return contentList;
        }

        try {
            Info info = yamlTemplate.getInfo();

            contentList.add(addHeading(3, "Overview"));
            addTextSection(contentList, 4, "Severity", issue.getSeverity().name());

            if (info != null) {
                addTextSection(contentList, 4, "Impact", info.getImpact());
                addListSection(contentList, 4, "Tags", info.getTags());
                addComplianceSection(contentList, info.getCompliance());
                addListSection(contentList, 4, "CWE", info.getCwe());
                addListSection(contentList, 4, "CVE", info.getCve());
                addListSection(contentList, 4, "References", info.getReferences());

                String remediationText = StringUtils.isNotBlank(info.getRemediation())
                    ? info.getRemediation()
                    : remediation != null ? remediation.getRemediationText() : "No remediation provided.";
                addTextSection(contentList, 3, "Remediation", remediationText);
            }
            addListSection(contentList, 3, "Timelines (UTC)", getIssueTimelines(issue));
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e,
                "Error while adding additional issue details in Jira Payload: " + e.getMessage(),
                LoggerMaker.LogDb.DASHBOARD);
        }

        return contentList;
    }

    private static List<String> getIssueTimelines(TestingRunIssues issue) {
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
        return timelines;
    }

    private static void addTextSection(List<BasicDBObject> list, int level, String heading, String text) {
        list.add(addHeading(level, heading));
        list.add(addParagraph(text));
    }

    private static void addListSection(List<BasicDBObject> list, int level, String heading, List<String> items) {
        if (CollectionUtils.isNotEmpty(items)) {
            list.add(addHeading(level, heading));
            list.add(addList(items));
        }
    }

    private static void addComplianceSection(List<BasicDBObject> list, ComplianceMapping compliance) {
        if (compliance == null || MapUtils.isEmpty(compliance.getMapComplianceToListClauses())) return;

        list.add(addHeading(4, "Compliance"));
        for (Map.Entry<String, List<String>> entry : compliance.getMapComplianceToListClauses().entrySet()) {
            list.add(addHeading(5, entry.getKey()));
            list.add(CollectionUtils.isNotEmpty(entry.getValue())
                ? addList(entry.getValue())
                : addParagraph("No clauses available."));
        }
    }

    private static BasicDBObject addHeading(int level, String text) {
        return new BasicDBObject("type", "heading")
            .append("attrs", new BasicDBObject("level", level))
            .append("content", Collections.singletonList(
                new BasicDBObject("type", "text").append("text", StringUtils.defaultString(text, ""))));
    }

    private static BasicDBObject addParagraph(String text) {
        return new BasicDBObject("type", "paragraph")
            .append("content", Collections.singletonList(
                new BasicDBObject("type", "text").append("text", StringUtils.defaultString(text, ""))));
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
                        Collections.singletonList(
                            new BasicDBObject("type", "text").append("text", StringUtils.defaultString(text, ""))))
            ));
    }
}