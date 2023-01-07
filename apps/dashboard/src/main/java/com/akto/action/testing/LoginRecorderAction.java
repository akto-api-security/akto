package com.akto.action.testing;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.akto.action.UserAction;
import com.akto.dao.RecordedLoginInputDao;
import com.akto.dao.context.Context;
import com.akto.dao.testing.LoginFlowStepsDao;
import com.akto.util.RecordedLoginFlowUtil;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;


public class LoginRecorderAction extends UserAction {
    
    private String content;

    private String tokenFetchCommand;

    private String token;

    private String nodeId;

    private static final Logger logger = LoggerFactory.getLogger(LoginRecorderAction.class);

    private static final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();

    public String uploadRecordedFlow() {

        Integer userId = getSUser().getId();

        Bson filter = Filters.and(
            Filters.eq("userId", userId)
        );
        Bson update = Updates.combine(
            Updates.set("content", content),
            Updates.set("tokenFetchCommand", tokenFetchCommand),
            Updates.setOnInsert("createdAt", Context.now()),
            Updates.set("updatedAt", Context.now())
        );
        RecordedLoginInputDao.instance.updateOne(filter, update);

        String payload = this.content.toString();

        // try {
        //     RecordedLoginFlowUtil.triggerFlow(tokenFetchCommand, payload);
        // } catch (Exception e) {
        //     logger.error("error running recorded flow " + e.getMessage());
        //     return ERROR.toUpperCase();
        // }

        executorService.schedule( new Runnable() {
            public void run() {
                try {
                    RecordedLoginFlowUtil.triggerFlow(tokenFetchCommand, payload);
                } catch (Exception e) {
                    logger.error("error running recorded flow " + e.getMessage());
                }
            }
        }, 1, TimeUnit.SECONDS);

        return SUCCESS.toUpperCase();
    }

    public String fetchRecordedFlowOutput() {

        try {
            token = RecordedLoginFlowUtil.fetchToken();
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
}
