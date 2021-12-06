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
import com.akto.oas.OpenAPIValidator;
import com.akto.oas.scan.JobExecutor;
import com.akto.oas.Issue;
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

    public String fetchAuditResults() {
        BasicDBObject ret = new BasicDBObject();
        this.auditResult = new BasicDBObject("data", ret);

        BasicDBObject errors = new BasicDBObject();

        APISpec spec = APISpecDao.instance.findById(0);

        int specId =  spec == null ? 0 : spec.getId();
        ret.append("auditId", Context.now()).append("contentId", specId).append("errors", errors);
        if (spec != null) {
            List<Issue> issues = new OpenAPIValidator().validateMain(spec.getContent());
        

            for (Issue issue: issues) {
                if (issue.getMessage().contains("exampleSetFlag")) 
                    continue;
    
                String path = "root."+StringUtils.join(issue.getPath(), "."); 
                BasicDBList errorsForThisPath = (BasicDBList) errors.get(path);
    
                if (path.startsWith("root.components.")) {
                    ArrayList<String> newPath = new ArrayList<>();
                    for(int i = 1 ; i < issue.getPath().size();i++) {
                        newPath.add(issue.getPath().get(i));
                    }
                    issue.setPath(newPath);
                    path = "root."+StringUtils.join(issue.getPath(), ".");
                }

                if (errorsForThisPath == null) {
                    errorsForThisPath = new BasicDBList();
                    errors.put(path, errorsForThisPath);
                }
    
                errorsForThisPath.add(issue);
            }
            ret.append("content", BasicDBObject.parse(spec.getContent())).append("filename", spec.getFilename());
        }

        

        return Action.SUCCESS.toUpperCase();

    }

    public String saveContentAndFetchAuditResults() {
        int userId = getSUser().getId();
        Type type = Type.JSON;
        String content = apiSpec.toJson();

        APISpec spec = new APISpec(type, userId, filename, content);

        spec.setId(0);

        Bson updates = 
            Updates.combine(
                Updates.set("userId", userId),
                Updates.set("content", content),
                Updates.set("filename", filename),
                Updates.set("type", Type.JSON.name())
            );

        APISpecDao.instance.updateOne(Filters.eq("_id", 0), updates);

        return fetchAuditResults();
    }


    BasicDBObject authProtocolSettings;
    BasicDBObject testEnvSettings;
    BasicDBObject authProtocolSpecs;
    int contentId;
    BasicDBObject testConfigId = null;
    public String saveTestConfig() {

        int maxRequestsPerSec = testEnvSettings.getInt("rate");
        int maxResponseTime = testEnvSettings.getInt("waitTime");
        String logLevel = testEnvSettings.getString("logLevel").toUpperCase();
        
        Map<String, APIAuth> authSettings = new HashMap<String, APIAuth>();

        for(String key: authProtocolSpecs.keySet()) {
            HashMap<String, Object> specs = (HashMap<String, Object>) (authProtocolSpecs.get(key));
            APIAuth.Type apiAuthType = APIAuth.Type.valueOf(specs.getOrDefault("type", "").toString().toUpperCase());

            HashMap<String, Object> settings = (HashMap<String, Object>) (authProtocolSettings.get(key));

            switch (apiAuthType) {
                case APIKEY: 
                    String apiKey = settings.getOrDefault("apiKey", "").toString();
                    Placement placement = Placement.valueOf(specs.getOrDefault("in", "").toString().toUpperCase());
                    String keyName = specs.getOrDefault("name", "").toString();
                    authSettings.put(key, new APIAuthAPIKey(apiKey, placement, keyName));
                    break;
                case BASIC: 
                    authSettings.put(key, new APIAuthBasic(settings.getOrDefault("username", "").toString(), settings.getOrDefault("password", "").toString()));
                    break;
                case OAUTH2: 
                    authSettings.put(key, new APIAuthOAuth(settings.getOrDefault("token", "").toString()));
                    break;
            }
            
        }

        TestEnvSettings allSettings = 
            new TestEnvSettings(LogLevel.valueOf(logLevel), authSettings, maxRequestsPerSec, maxResponseTime, contentId);

        TestEnvSettingsDao.instance.insertOne(allSettings);

        TestRun testRunDetails = new JobExecutor().execute(contentId, allSettings.getId());

        testConfigId = new BasicDBObject("data", testRunDetails);

        return Action.SUCCESS.toUpperCase();
    }

    private BasicDBList attemptIds;
    public String fetchTestResults() {
        List<Attempt> attempts = AttemptsDao.instance.findAll("_id", attemptIds);

        testConfigId = new BasicDBObject("data", attempts);

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
