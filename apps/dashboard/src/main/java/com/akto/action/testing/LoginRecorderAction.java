package com.akto.action.testing;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.io.File;

import org.bson.conversions.Bson;

import com.akto.action.UserAction;
import com.akto.dao.RecordedLoginInputDao;
import com.akto.dao.context.Context;
import com.akto.dao.testing.LoginFlowStepsDao;
import com.akto.dto.RecordedLoginFlowInput;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.RecordedLoginFlowUtil;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;


public class LoginRecorderAction extends UserAction {

    private static final LoggerMaker loggerMaker = new LoggerMaker(LoginRecorderAction.class, LogDb.DASHBOARD);;

    private String content;

    private String tokenFetchCommand;

    private String token;

    private String nodeId;

    private Boolean tokenFetchInProgress;


    private static final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();

    public String uploadRecordedFlow() {

        String payload = this.content.toString();

        int accountId = Context.accountId.get();

        // Delete recorded login flow input for user (if exists)
        int userId = getSUser().getId();
        if (userId != 0) {
            RecordedLoginInputDao.instance.deleteAll(Filters.eq("userId", userId));
        }

        executorService.schedule( new Runnable() {
            public void run() {
                try {
                    Context.accountId.set(accountId);
                    File tmpOutputFile = File.createTempFile("output", ".json");
                    File tmpErrorFile = File.createTempFile("recordedFlowOutput", ".txt");
                    RecordedLoginFlowUtil.triggerFlow(tokenFetchCommand, payload, tmpOutputFile.getPath(), tmpErrorFile.getPath(), getSUser().getId());
                } catch (Exception e) {
                    loggerMaker.errorAndAddToDb(e,"error running recorded flow " + e.toString(), LogDb.DASHBOARD);
                }
            }
        }, 1, TimeUnit.SECONDS);

        return SUCCESS.toUpperCase();
    }

    public String fetchRecordedFlowOutput() {

        RecordedLoginFlowInput recordedLoginInput = RecordedLoginInputDao.instance.findOne(Filters.eq("userId", getSUser().getId()));

        if (recordedLoginInput == null) {
            tokenFetchInProgress = true;
            return SUCCESS.toUpperCase();
        }

        try {
            token = RecordedLoginFlowUtil.fetchToken(recordedLoginInput.getOutputFilePath(), recordedLoginInput.getErrorFilePath());
        } catch(Exception e) {
            addActionError(e.getMessage());
            return ERROR.toUpperCase();
        }

        Map<String, Object> valuesMap = new HashMap<>();
        valuesMap.put(nodeId + ".response.body.token", token);

        Integer userId = getSUser().getId();

        Bson filter = Filters.and(
            Filters.eq("userId", userId)
        );
        Bson update = Updates.set("valuesMap", valuesMap);
        LoginFlowStepsDao.instance.updateOne(filter, update);

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
}
