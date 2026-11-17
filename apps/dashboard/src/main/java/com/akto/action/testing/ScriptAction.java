package com.akto.action.testing;

import java.util.List;

import org.apache.commons.lang3.NotImplementedException;

import com.akto.action.UserAction;
import com.akto.audit_logs_util.Audit;
import com.akto.dao.context.Context;
import com.akto.dto.User;
import com.akto.dto.audit_logs.Operation;
import com.akto.dto.audit_logs.Resource;
import com.akto.dto.testing.config.TestScript;
import com.akto.dto.testing.config.TestScriptSummary;
import com.akto.listener.InitializerListener;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.service.scripts.TestScriptException;
import com.akto.service.scripts.TestScriptService;
import com.akto.service.scripts.validation.ReplayOutcome;
import com.akto.service.scripts.validation.ScriptValidationRequest;
import com.akto.service.scripts.validation.TestScriptValidationService;
import com.opensymphony.xwork2.Action;

import lombok.Getter;
import lombok.Setter;

/** Request handling for the pre/post request script library. Rules live in TestScriptService. */
public class ScriptAction extends UserAction {

    private static final LoggerMaker loggerMaker = new LoggerMaker(ScriptAction.class, LogDb.DASHBOARD);

    private final TestScriptService testScriptService = new TestScriptService();
    private final TestScriptValidationService validationService = new TestScriptValidationService();

    @Override
    public String execute() throws Exception {
        throw new NotImplementedException();
    }

    // request
    @Getter @Setter
    private String scriptId;
    @Setter
    private String name;
    @Setter
    private String scriptType;
    @Setter
    private String javascript;

    // api/validateTestScript; see service/scripts/validation/CLAUDE.md
    @Setter
    private ScriptValidationRequest validationRequest;

    // response
    @Getter
    private TestScript testScript;
    @Getter
    private ReplayOutcome scriptValidationResult;
    @Getter
    private List<TestScriptSummary> scripts;

    public String fetchAllScripts() {
        try {
            InitializerListener.ensureTestScriptsMigrated();
            this.scripts = testScriptService.listSummaries();
            return Action.SUCCESS.toUpperCase();
        } catch (Exception e) {
            return unexpected(e, "fetchAllScripts", "Failed to load scripts");
        }
    }

    public String fetchScript() {
        try {
            this.testScript = testScriptService.get(this.scriptId);
            return Action.SUCCESS.toUpperCase();
        } catch (TestScriptException e) {
            return rejected(e);
        } catch (Exception e) {
            return unexpected(e, "fetchScript", "Failed to load script");
        }
    }

    public String addScript() {
        try {
            User user = getSUser();
            testScriptService.requireWriteAccess(user, Context.accountId.get());
            this.testScript = testScriptService.create(this.name, this.scriptType, this.javascript, loginOf(user));
            return Action.SUCCESS.toUpperCase();
        } catch (TestScriptException e) {
            return rejected(e);
        } catch (Exception e) {
            return unexpected(e, "addScript", "Failed to create script");
        }
    }

    public String updateScript() {
        try {
            User user = getSUser();
            testScriptService.requireWriteAccess(user, Context.accountId.get());
            this.testScript = testScriptService.updateJavascript(this.scriptId, this.javascript, loginOf(user));
            return Action.SUCCESS.toUpperCase();
        } catch (TestScriptException e) {
            return rejected(e);
        } catch (Exception e) {
            return unexpected(e, "updateScript", "Failed to update script");
        }
    }

    @Audit(description = "User deleted a test script", resource = Resource.TEST_SCRIPT, operation = Operation.DELETE, metadataGenerators = {"getScriptId"})
    public String deleteScript() {
        try {
            testScriptService.requireWriteAccess(getSUser(), Context.accountId.get());
            testScriptService.delete(this.scriptId);
            return Action.SUCCESS.toUpperCase();
        } catch (TestScriptException e) {
            return rejected(e);
        } catch (Exception e) {
            return unexpected(e, "deleteScript", "Failed to delete script");
        }
    }

    public String validateTestScript() {
        try {
            this.scriptValidationResult = validationService.validate(this.validationRequest, getSUser());
            return Action.SUCCESS.toUpperCase();
        } catch (TestScriptException e) {
            return rejected(e);
        } catch (Exception e) {
            return unexpected(e, "validateTestScript", "Failed to run the validation");
        }
    }

    private String rejected(TestScriptException e) {
        addActionError(e.getMessage());
        return Action.ERROR.toUpperCase();
    }

    private String unexpected(Exception e, String operation, String userMessage) {
        loggerMaker.errorAndAddToDb(e, "error in " + operation + " for script " + this.scriptId);
        addActionError(userMessage);
        return Action.ERROR.toUpperCase();
    }

    private static String loginOf(User user) {
        return user == null ? null : user.getLogin();
    }
}
