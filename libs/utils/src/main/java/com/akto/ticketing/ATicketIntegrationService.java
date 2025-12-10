package com.akto.ticketing;

import com.akto.dao.context.Context;
import com.akto.dao.test_editor.YamlTemplateDao;
import com.akto.dao.testing.TestingRunResultDao;
import com.akto.dao.testing_run_findings.TestingRunIssuesDao;
import com.akto.dto.test_editor.Info;
import com.akto.dto.test_editor.YamlTemplate;
import com.akto.dto.test_run_findings.TestingIssuesId;
import com.akto.dto.test_run_findings.TestingRunIssues;
import com.akto.dto.testing.TestingRunResult;
import com.akto.log.LoggerMaker;
import com.akto.util.Constants;
import com.akto.util.enums.GlobalEnums;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Updates;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public abstract class ATicketIntegrationService<T> {

    protected static final LoggerMaker logger = new LoggerMaker(ATicketIntegrationService.class,
        LoggerMaker.LogDb.DASHBOARD);

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TicketCreationResult {

        private int successCount;
        private int failCount;
        private int existingCount;
        private String message;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TicketInfo {
        private String ticketId;
        private String ticketUrl;
    }

    public final TicketCreationResult createTickets(List<TestingIssuesId> testingIssuesIdList, String projectIdentifier, String workItemType)
        throws Exception {
        validateInput(testingIssuesIdList, projectIdentifier);

        T integration = fetchIntegration();
        if (integration == null) {
            throw new Exception(getIntegrationName() + " Integration is not configured.");
        }

        projectIdentifier = validateAndGetProjectIdentifier(integration, projectIdentifier);

        int successCount = 0;
        int failCount = 0;
        int existingCount = 0;
        String ticketFieldUrlName = getTicketUrlFieldName();
        List<TestingRunIssues> existingIssues = TestingRunIssuesDao.instance.findAll(
            Filters.and(
                Filters.in(Constants.ID, testingIssuesIdList),
                Filters.exists(ticketFieldUrlName, true),
                Filters.ne(ticketFieldUrlName, "")
            )
        );
        existingCount = existingIssues != null ? existingIssues.size() : 0;

        String authToken = getAuthenticationToken(integration);
        if (StringUtils.isBlank(authToken)) {
            throw new Exception(getIntegrationName() + " authentication token is missing.");
        }

        List<String> testSubCategories = testingIssuesIdList.stream()
            .map(TestingIssuesId::getTestSubCategory)
            .distinct()
            .collect(Collectors.toList());

        List<YamlTemplate> yamlTemplates = YamlTemplateDao.instance.findAll(
            Filters.in(Constants.ID, testSubCategories),
            Projections.include(YamlTemplate.INFO)
        );

        Map<String, Info> testSubCategoryToInfoMap = new HashMap<>();
        for (YamlTemplate template : yamlTemplates) {
            if (template != null && template.getInfo() != null) {
                testSubCategoryToInfoMap.put(template.getId(), template.getInfo());
            }
        }

        for (TestingIssuesId issueId : testingIssuesIdList) {
            try {
                TestingRunIssues existingIssue = TestingRunIssuesDao.instance.findOne(
                    Filters.eq(Constants.ID, issueId)
                );

                if (existingIssue != null && StringUtils.isNotBlank(getExistingTicketUrl(existingIssue))) {
                    logger.infoAndAddToDb(getIntegrationName() + " ticket already exists for issue: " + issueId,
                        LoggerMaker.LogDb.DASHBOARD);
                    continue;
                }

                TestingRunResult testingRunResult = TestingRunResultDao.instance.findOne(
                    Filters.and(
                        Filters.eq(TestingRunResult.TEST_SUB_TYPE, issueId.getTestSubCategory()),
                        Filters.eq(TestingRunResult.API_INFO_KEY, issueId.getApiInfoKey())
                    )
                );

                if (testingRunResult == null) {
                    logger.errorAndAddToDb("TestingRunResult not found for issue: " + issueId,
                        LoggerMaker.LogDb.DASHBOARD);
                    failCount++;
                    continue;
                }

                Info testInfo = testSubCategoryToInfoMap.get(issueId.getTestSubCategory());

                if (testInfo == null) {
                    logger.errorAndAddToDb("YamlTemplate or Info not found for test: " + issueId.getTestSubCategory(),
                        LoggerMaker.LogDb.DASHBOARD);
                    failCount++;
                    continue;
                }

                GlobalEnums.Severity severity = existingIssue != null ?
                    existingIssue.getSeverity() : GlobalEnums.Severity.HIGH;

                TicketInfo ticketInfo = createTicketForIssue(
                    integration,
                    authToken,
                    issueId,
                    testInfo,
                    testingRunResult,
                    severity,
                    projectIdentifier,
                    workItemType
                );

                if (ticketInfo != null && StringUtils.isNotBlank(ticketInfo.getTicketUrl())) {
                    updateIssueWithTicketInfo(issueId, ticketInfo.getTicketId(), ticketInfo.getTicketUrl());
                    logger.infoAndAddToDb(
                        "Successfully created " + getIntegrationName() + " ticket for issue: " + issueId,
                        LoggerMaker.LogDb.DASHBOARD);
                    successCount++;
                } else {
                    logger.errorAndAddToDb("Failed to create " + getIntegrationName() + " ticket for issue: " + issueId,
                        LoggerMaker.LogDb.DASHBOARD);
                    failCount++;
                }

            } catch (Exception e) {
                logger.errorAndAddToDb(
                    "Error creating " + getIntegrationName() + " ticket for issue " + issueId + ": " + e.getMessage(),
                    LoggerMaker.LogDb.DASHBOARD);
                failCount++;
            }
        }

        String message = buildResultMessage(testingIssuesIdList.size(), successCount, failCount, existingCount);

        return new TicketCreationResult(successCount, failCount, existingCount, message);
    }

    protected void validateInput(List<TestingIssuesId> testingIssuesIdList, String projectIdentifier) throws Exception {
        if (testingIssuesIdList == null || testingIssuesIdList.isEmpty()) {
            throw new Exception("Cannot create " + getIntegrationName() + " tickets without testing issues.");
        }
    }

    protected String buildResultMessage(int totalIssues, int successCount, int failCount, int existingCount) {
        if (successCount == 0 && failCount == 0 && existingCount == totalIssues) {
            return "All issues already have " + getIntegrationName() + " tickets.";
        }

        if (successCount == totalIssues && failCount == 0) {
            return String.format("Successfully created %d %s ticket%s.",
                successCount, getIntegrationName(), successCount > 1 ? "s" : "");
        }

        if (successCount == 0 && failCount == totalIssues) {
            return String.format("Failed to create %d %s ticket%s.",
                failCount, getIntegrationName(), failCount > 1 ? "s" : "");
        }

        return String.format("Created %d, failed %d, already existed %d.",
            successCount, failCount, existingCount);
    }

    private void updateIssueWithTicketInfo(TestingIssuesId issueId, String ticketId, String ticketUrl) {
        TestingRunIssuesDao.instance.updateOneNoUpsert(
            Filters.eq(Constants.ID, issueId),
            Updates.combine(
                Updates.set(getTicketUrlFieldName(), ticketUrl),
                Updates.set(TestingRunIssues.TICKET_ID, ticketId),
                Updates.set(TestingRunIssues.TICKET_SOURCE, getTicketSource()),
                Updates.set(TestingRunIssues.TICKET_LAST_UPDATED_AT, Context.now())
            )
        );
    }

    protected abstract String getIntegrationName();

    protected abstract String getTicketUrlFieldName();

    protected abstract GlobalEnums.TicketSource getTicketSource();

    protected abstract T fetchIntegration();

    protected abstract String validateAndGetProjectIdentifier(T integration, String projectIdentifier) throws Exception;

    protected abstract String getAuthenticationToken(T integration);

    protected abstract String getExistingTicketUrl(TestingRunIssues issue);

    protected abstract TicketInfo createTicketForIssue(
        T integration,
        String authToken,
        TestingIssuesId issueId,
        Info testInfo,
        TestingRunResult testingRunResult,
        GlobalEnums.Severity severity,
        String projectIdentifier,
        String workItemType
    );
}
