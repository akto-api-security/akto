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
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.opensymphony.xwork2.Action;

public class ScriptAction extends UserAction {

    @Override
    public String execute() throws Exception {
        throw new NotImplementedException();
    }

    private TestScript testScript;

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

        TestScriptsDao.instance.insertOne(
            new TestScript(
                UUID.randomUUID().toString(),
                this.testScript.getJavascript(),
                TestScript.Type.PRE_REQUEST,
                getSUser().getLogin(),
                Context.now()
            )
        );

        return Action.SUCCESS.toUpperCase();
    }
    
    public String fetchScript() {
        this.testScript = TestScriptsDao.instance.findOne(new BasicDBObject());
        return Action.SUCCESS.toUpperCase();
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
}
