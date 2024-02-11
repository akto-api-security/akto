package com.akto.action;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import com.akto.dao.APISpecDao;
import com.akto.dao.AttemptsDao;
import com.akto.dao.TestEnvSettingsDao;
import com.akto.dao.context.Context;
import com.akto.dto.APISpec;
import com.akto.dto.Attempt;
import com.akto.dto.TestEnvSettings;
import com.akto.dto.TestRun;
import com.akto.dto.APISpec.Type;
import com.akto.dto.TestEnvSettings.LogLevel;
import com.akto.dto.auth.APIAuth;
import com.akto.dto.auth.APIAuthAPIKey;
import com.akto.dto.auth.APIAuthBasic;
import com.akto.dto.auth.APIAuthOAuth;
import com.akto.dto.auth.APIAuthAPIKey.Placement;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.opensymphony.xwork2.Action;

import org.apache.commons.lang3.StringUtils;
import org.bson.conversions.Bson;

import java.util.List;

public class AuditAction extends UserAction {

    String filename = null;
    BasicDBObject apiSpec = null;
    BasicDBObject auditResult = null;
    int apiCollectionId;

    public String fetchAuditResults() {
        return Action.SUCCESS.toUpperCase();

    }

    public String loadContent() {
        return Action.SUCCESS.toUpperCase();
    }

    public String saveContent() {
        return Action.SUCCESS.toUpperCase();
    }

    BasicDBObject authProtocolSettings;
    BasicDBObject testEnvSettings;
    BasicDBObject authProtocolSpecs;
    int contentId;
    BasicDBObject testConfigId = null;
    public String saveTestConfig() {
        return Action.SUCCESS.toUpperCase();
    }

    private BasicDBList attemptIds;
    public String fetchTestResults() {
        return Action.SUCCESS.toUpperCase();
    }


    public String getFilename() {
        return this.filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public BasicDBList getAttemptIds() {
        return this.attemptIds;
    }

    public void setAttemptIds(BasicDBList attemptIds) {
        this.attemptIds = attemptIds;
    }

    public int getApiCollectionId() {
        return this.apiCollectionId;
    }
    
    public void setApiCollectionId(int apiCollectionId) {
        this.apiCollectionId = apiCollectionId;
    }

    public BasicDBObject getApiSpec() {
        return this.apiSpec;
    }

    public void setApiSpec(BasicDBObject apiSpec) {
        this.apiSpec = apiSpec;
    }

    public BasicDBObject getAuditResult() {
        return this.auditResult;
    }

    public void setAuditResult(BasicDBObject auditResult) {
        this.auditResult = auditResult;
    }
    
    public BasicDBObject getAuthProtocolSettings() {
        return this.authProtocolSettings;
    }

    public void setAuthProtocolSettings(BasicDBObject authProtocolSettings) {
        this.authProtocolSettings = authProtocolSettings;
    }

    
    public BasicDBObject getTestEnvSettings() {
        return this.testEnvSettings;
    }

    public void setTestEnvSettings(BasicDBObject testEnvSettings) {
        this.testEnvSettings = testEnvSettings;
    }

    public BasicDBObject getAuthProtocolSpecs() {
        return this.authProtocolSpecs;
    }

    public void setAuthProtocolSpecs(BasicDBObject authProtocolSpecs) {
        this.authProtocolSpecs = authProtocolSpecs;
    }

    public int getContentId() {
        return this.contentId;
    } 

    public void setContentId(int contentId) {
        this.contentId = contentId;
    }

    public BasicDBObject getTestConfigId() {
        return this.testConfigId;
    }

    public void setTestConfigId(BasicDBObject testConfigId) {
        this.testConfigId = testConfigId;
    }
}
