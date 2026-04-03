package com.akto.action.testing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.io.File;

import org.bson.conversions.Bson;

import com.akto.action.UserAction;
import com.akto.dao.AccountsDao;
import com.akto.dao.RecordedLoginInputDao;
import com.akto.dao.RecordedLoginScreenshotDao;
import com.akto.dao.context.Context;
import com.akto.dao.test_editor.TestingRunPlaygroundDao;
import com.akto.dao.testing.LoginFlowStepsDao;
import com.akto.dto.Account;
import com.akto.dto.test_editor.TestingRunPlayground;
import com.akto.dto.testing.TestingRun;
import com.akto.dto.RecordedLoginFlowInput;
import com.akto.dto.RecordedLoginFlowScreenshot;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.RecordedLoginFlowUtil;
import com.akto.util.Constants;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;

import lombok.Getter;
import lombok.Setter;

import org.apache.commons.lang3.StringUtils;
import org.bson.types.ObjectId;

public class LoginRecorderAction extends UserAction {

    private static final LoggerMaker loggerMaker = new LoggerMaker(LoginRecorderAction.class, LogDb.DASHBOARD);

    private String content;

    private String tokenFetchCommand;

    private String token;

    private String nodeId;

    private Boolean tokenFetchInProgress;

    private String roleName;

    @Getter
    @Setter
    private List<String> screenshotsBase64;

    @Getter
    @Setter
    private int screenshotsUpdatedAt;

    private String miniTestingServiceName;

    private static final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();

    private boolean shouldRunRecordedFlowOnMiniTesting() {
        if (StringUtils.isBlank(miniTestingServiceName)) {
            return false;
        }
        Account account = AccountsDao.instance.findOne(Filters.eq(Constants.ID, Context.accountId.get()));
        return account != null && account.getHybridTestingEnabled();
    }

    public String uploadRecordedFlow() {

        String payload = this.content.toString();

        int accountId = Context.accountId.get();

        // Delete recorded login flow input for user (if exists)
        int userId = getSUser().getId();
        if (userId != 0) {
            RecordedLoginInputDao.instance.deleteAll(Filters.eq("userId", userId));
        }

        if (shouldRunRecordedFlowOnMiniTesting()) {
            TestingRunPlayground playground = new TestingRunPlayground();
            playground.setTestingRunPlaygroundType(TestingRunPlayground.TestingRunPlaygroundType.RECORDED_JSON_FLOW);
            playground.setState(TestingRun.State.SCHEDULED);
            playground.setCreatedAt(Context.now());
            playground.setMiniTestingName(miniTestingServiceName.trim());
            playground.setRecordedFlowOwnerUserId(userId);
            playground.setRecordedFlowContent(payload);
            playground.setRecordedFlowTokenFetchCommand(tokenFetchCommand);
            playground.setRecordedFlowRoleName(roleName != null ? roleName.trim() : null);
            TestingRunPlaygroundDao.instance.insertOne(playground);
            return SUCCESS.toUpperCase();
        }

        executorService.schedule( new Runnable() {
            public void run() {
                try {
                    Context.accountId.set(accountId);
                    File tmpOutputFile = File.createTempFile("output", ".json");
                    File tmpErrorFile = File.createTempFile("recordedFlowOutput", ".txt");
                    RecordedLoginFlowUtil.triggerFlow(tokenFetchCommand, payload, tmpOutputFile.getPath(), tmpErrorFile.getPath(), userId, roleName);
                } catch (Exception e) {
                    loggerMaker.errorAndAddToDb(e,"error running recorded flow " + e.toString(), LogDb.DASHBOARD);
                }
            }
        }, 1, TimeUnit.SECONDS);

        return SUCCESS.toUpperCase();
    }

    private void applyExtractedTokenToLoginFlowSteps(String extractedToken) {
        Map<String, Object> valuesMap = new HashMap<>();
        valuesMap.put(nodeId + ".response.body.token", extractedToken);
        int userId = getSUser().getId();
        Bson filter = Filters.eq("userId", userId);
        LoginFlowStepsDao.instance.updateOne(filter, Updates.set("valuesMap", valuesMap));
    }

    private void clearHybridRecordedFlowFieldsOnPlayground(ObjectId playgroundId) {
        TestingRunPlaygroundDao.instance.updateOneNoUpsert(
                Filters.eq(Constants.ID, playgroundId),
                Updates.combine(
                        Updates.unset(TestingRunPlayground.RECORDED_FLOW_TOKEN_RESULT),
                        Updates.unset(TestingRunPlayground.RECORDED_FLOW_ERROR_MESSAGE)));
    }

    public String fetchRecordedFlowOutput() {

        int userIdInt = getSUser().getId();
        boolean hybridPoll = StringUtils.isNotBlank(miniTestingServiceName);

        RecordedLoginFlowInput recordedLoginInput = null;
        if (!hybridPoll) {
            recordedLoginInput = RecordedLoginInputDao.instance.findOne(Filters.eq("userId", userIdInt));
            if (recordedLoginInput != null) {
                String inlineToken = recordedLoginInput.getTokenResult();
                if (inlineToken != null && !inlineToken.trim().isEmpty()) {
                    token = inlineToken;
                    applyExtractedTokenToLoginFlowSteps(token);
                    return SUCCESS.toUpperCase();
                }
            }
        }

        List<Bson> hybridCriteria = new ArrayList<>();
        hybridCriteria.add(Filters.eq(TestingRunPlayground.TESTING_RUN_PLAYGROUND_TYPE,
                TestingRunPlayground.TestingRunPlaygroundType.RECORDED_JSON_FLOW.name()));
        hybridCriteria.add(Filters.eq(TestingRunPlayground.STATE, TestingRun.State.COMPLETED.name()));
        hybridCriteria.add(Filters.or(
                Filters.exists(TestingRunPlayground.RECORDED_FLOW_TOKEN_RESULT, true),
                Filters.exists(TestingRunPlayground.RECORDED_FLOW_ERROR_MESSAGE, true)));

        List<Bson> ownerOrLegacy = new ArrayList<>();
        ownerOrLegacy.add(Filters.eq(TestingRunPlayground.RECORDED_FLOW_OWNER_USER_ID, userIdInt));
        if (hybridPoll) {
            ownerOrLegacy.add(Filters.and(
                    Filters.exists(TestingRunPlayground.RECORDED_FLOW_OWNER_USER_ID, false),
                    Filters.eq("miniTestingName", miniTestingServiceName.trim())));
        }
        hybridCriteria.add(Filters.or(ownerOrLegacy));

        if (hybridPoll) {
            hybridCriteria.add(Filters.eq("miniTestingName", miniTestingServiceName.trim()));
        }
        Bson hybridPlaygroundFilter = Filters.and(hybridCriteria);
        TestingRunPlayground hybridPg = TestingRunPlaygroundDao.instance.findLatestOne(hybridPlaygroundFilter);

        if (hybridPg != null) {
            String err = hybridPg.getRecordedFlowErrorMessage();
            if (err != null && !err.isEmpty()) {
                addActionError(err);
                clearHybridRecordedFlowFieldsOnPlayground(hybridPg.getId());
                return ERROR.toUpperCase();
            }

            String hybridToken = hybridPg.getRecordedFlowTokenResult();
            if (hybridToken == null || hybridToken.isEmpty()) {
                tokenFetchInProgress = true;
                return SUCCESS.toUpperCase();
            }

            token = hybridToken;
            applyExtractedTokenToLoginFlowSteps(token);
            clearHybridRecordedFlowFieldsOnPlayground(hybridPg.getId());
            return SUCCESS.toUpperCase();
        }

        if (!hybridPoll && recordedLoginInput != null) {
            try {
                token = RecordedLoginFlowUtil.fetchToken(recordedLoginInput);
            } catch (Exception e) {
                addActionError(e.getMessage());
                return ERROR.toUpperCase();
            }
            applyExtractedTokenToLoginFlowSteps(token);
            return SUCCESS.toUpperCase();
        }

        tokenFetchInProgress = true;
        return SUCCESS.toUpperCase();
    }

    public String fetchRecordedLoginScreenshots() {
        if (roleName == null || roleName.trim().isEmpty()) {
            addActionError("roleName is required");
            return ERROR.toUpperCase();
        }
        RecordedLoginFlowScreenshot doc = RecordedLoginScreenshotDao.instance.findOne(
                Filters.and(Filters.eq("roleName", roleName.trim()), Filters.eq("userId", getSUser().getId())));
        if (doc == null) {
            screenshotsBase64 = new ArrayList<>();
            screenshotsUpdatedAt = 0;
        } else {
            screenshotsBase64 = doc.getScreenshotsBase64() != null ? doc.getScreenshotsBase64() : new ArrayList<>();
            screenshotsUpdatedAt = doc.getUpdatedAt();
        }
        return SUCCESS.toUpperCase();
    }

    public String getContent() {
        return this.content;
    }

    public String getNodeId() {
        return this.nodeId;
    }

    public String getTokenFetchCommand() {
        return this.tokenFetchCommand;
    }
    public String getToken() {
        return this.token;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public void setTokenFetchCommand(String tokenFetchCommand) {
        this.tokenFetchCommand = tokenFetchCommand;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public Boolean getTokenFetchInProgress() {
        return this.tokenFetchInProgress;
    }

    public void setTokenFetchInProgress(Boolean tokenFetchInProgress) {
        this.tokenFetchInProgress = tokenFetchInProgress;
    }

    public String getRoleName() {
        return roleName;
    }

    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }

    public String getMiniTestingServiceName() {
        return miniTestingServiceName;
    }

    public void setMiniTestingServiceName(String miniTestingServiceName) {
        this.miniTestingServiceName = miniTestingServiceName;
    }
}
