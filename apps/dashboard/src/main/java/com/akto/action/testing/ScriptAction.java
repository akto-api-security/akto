package com.akto.action.testing;

import java.util.UUID;

import org.apache.commons.lang3.NotImplementedException;
import org.bson.conversions.Bson;

import com.akto.action.UserAction;
import com.akto.dao.context.Context;
import com.akto.dao.testing.config.TestScriptsDao;
import com.akto.dto.User;
import com.akto.dto.testing.config.TestScript;
import com.akto.util.DashboardMode;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.opensymphony.xwork2.Action;

public class ScriptAction extends UserAction {

    @Override
    public String execute() throws Exception {
        throw new NotImplementedException();
    }

    private TestScript testScript;
    private String scriptType;

    public boolean aktoUser(){
        User user = getSUser();

        if(user==null || user.getLogin()==null || user.getLogin().isEmpty()){
            return false;
        }

        if(user.getLogin().contains("@akto.io")){
            return true;
        }
        return false;
    }

    public String addScript() {

        if (DashboardMode.isSaasDeployment() && !aktoUser()) {
            return Action.ERROR.toUpperCase();
        }

        if (this.testScript == null || this.testScript.getJavascript() == null) {
            return Action.ERROR.toUpperCase();
        }

        TestScript.Type type = resolveScriptType(this.testScript.getType(), this.scriptType, TestScript.Type.PRE_REQUEST);
        TestScriptsDao.instance.insertOne(
            new TestScript(
                UUID.randomUUID().toString(),
                this.testScript.getJavascript(),
                type,
                getSUser().getLogin(),
                Context.now()
            )
        );

        return Action.SUCCESS.toUpperCase();
    }
    
    public String fetchScript() {
        TestScript.Type type = resolveScriptType(this.testScript != null ? this.testScript.getType() : null, this.scriptType, TestScript.Type.PRE_REQUEST);
        this.testScript = TestScriptsDao.instance.fetchTestScript(type);
        return Action.SUCCESS.toUpperCase();
    }

    private static TestScript.Type resolveScriptType(TestScript.Type fromTestScript, String scriptTypeStr, TestScript.Type defaultType) {
        if (fromTestScript != null) {
            return fromTestScript;
        }
        if (scriptTypeStr != null && !scriptTypeStr.isEmpty()) {
            try {
                return TestScript.Type.valueOf(scriptTypeStr);
            } catch (IllegalArgumentException e) {
                return defaultType;
            }
        }
        return defaultType;
    }
    
    public String updateScript() {

        if (DashboardMode.isSaasDeployment() && !aktoUser()) {
            return Action.ERROR.toUpperCase();
        }
        
        if (this.testScript == null || this.testScript.getJavascript() == null) {
            return Action.ERROR.toUpperCase();
        }

        Bson filterQ = Filters.eq("_id", testScript.getId());
        Bson updateQ =
            Updates.combine(
                Updates.set(TestScript.JAVASCRIPT, this.testScript.getJavascript()),
                Updates.set(TestScript.AUTHOR, getSUser().getLogin()),
                Updates.set(TestScript.LAST_UPDATED_AT, Context.now())
            );
        TestScriptsDao.instance.updateOne(filterQ, updateQ);
        return Action.SUCCESS.toUpperCase();
    }
    
    public TestScript getTestScript() {
        return testScript;
    }

    public void setTestScript(TestScript testScript) {
        this.testScript = testScript;
    }

    public String getScriptType() {
        return scriptType;
    }

    public void setScriptType(String scriptType) {
        this.scriptType = scriptType;
    }
}
