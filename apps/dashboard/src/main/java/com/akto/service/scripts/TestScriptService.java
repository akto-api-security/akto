package com.akto.service.scripts;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.akto.dao.context.Context;
import com.akto.dao.testing.TestingRunConfigDao;
import com.akto.dao.testing.config.TestScriptsDao;
import com.akto.dto.User;
import com.akto.dto.testing.config.ScriptSelection;
import com.akto.dto.testing.config.TestScript;
import com.akto.dto.testing.config.TestScriptSummary;
import com.akto.util.DashboardMode;
import com.mongodb.ErrorCategory;
import com.mongodb.MongoWriteException;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;

/**
 * Business rules for the pre/post request script library. Shared by ScriptAction (CRUD) and
 * StartTestAction (validating the scripts a testing run selects). See service/scripts/CLAUDE.md.
 */
public class TestScriptService {

    // Moved unchanged from ScriptAction. Pre-existing hardcoded allowlist; see CLAUDE.md open work.
    private static final Set<Integer> WHITE_LISTED_ACCOUNTS = new HashSet<>(Arrays.asList(
        1764738582,
        1789954857
    ));

    private final TestScriptValidator validator = new TestScriptValidator();

    public List<TestScriptSummary> listSummaries() {
        List<TestScriptSummary> summaries = new ArrayList<>();
        for (TestScript script : TestScriptsDao.instance.findAllSummaries()) {
            summaries.add(TestScriptSummary.from(script));
        }
        return summaries;
    }

    public TestScript get(String id) throws TestScriptException {
        return requireExisting(id);
    }

    public TestScript create(String rawName, String rawType, String javascript, String login) throws TestScriptException {
        requireLogin(login);
        TestScript.Type type = validator.validateType(rawType);
        String name = validator.validateName(rawName, type);
        String body = validator.validateJavascript(javascript);

        if (TestScriptsDao.instance.findByName(name) != null) {
            throw nameTaken(name);
        }

        int now = Context.now();
        TestScript script = new TestScript(UUID.randomUUID().toString(), name, body, type, login, login, now, now);
        try {
            TestScriptsDao.instance.insertOne(script);
        } catch (MongoWriteException e) {
            // a concurrent create (or a client retry) won the unique name index
            if (e.getError().getCategory() == ErrorCategory.DUPLICATE_KEY) {
                throw nameTaken(name);
            }
            throw e;
        }
        return script;
    }

    /** Only the body changes; name and type are immutable. */
    public TestScript updateJavascript(String id, String javascript, String login) throws TestScriptException {
        requireLogin(login);
        String body = validator.validateJavascript(javascript);
        requireExisting(id);

        TestScript before = TestScriptsDao.instance.updateOneNoUpsert(
            Filters.eq(TestScript.ID, id),
            Updates.combine(
                Updates.set(TestScript.JAVASCRIPT, body),
                Updates.set(TestScript.LAST_UPDATED_BY, login),
                Updates.set(TestScript.AUTHOR, login),
                Updates.set(TestScript.LAST_UPDATED_AT, Context.now())
            )
        );
        if (before == null) {
            throw notFound();
        }
        return TestScriptsDao.instance.findById(id);
    }

    public void delete(String id) throws TestScriptException {
        TestScript script = requireExisting(id);
        if (TestScript.isDefaultName(script.getName())) {
            throw new TestScriptException("Default scripts cannot be deleted; runs without a selected script use them");
        }
        if (TestingRunConfigDao.instance.existsReferenceToScript(id)) {
            throw new TestScriptException("Script is selected in a test configuration; choose a different script there before deleting it");
        }
        TestScriptsDao.instance.deleteAll(Filters.eq(TestScript.ID, id));
    }

    /**
     * Checks a script id a testing run wants to store. null (default) and "" (none) are always valid;
     * any other value must be an existing script of the given type.
     */
    public void validateRunSelection(String rawId, TestScript.Type type) throws TestScriptException {
        ScriptSelection selection = ScriptSelection.from(rawId, type);
        if (selection.getKind() != ScriptSelection.Kind.BY_ID) {
            return;
        }
        String label = type == TestScript.Type.PRE_REQUEST ? "pre-request" : "post-request";
        TestScript script = TestScriptsDao.instance.findById(selection.getScriptId());
        if (script == null) {
            throw new TestScriptException("Selected " + label + " script does not exist");
        }
        if (script.getType() != type) {
            throw new TestScriptException("Selected " + label + " script '" + script.getName() + "' is not a " + label + " script");
        }
    }

    /** Same rule ScriptAction applied before the library existed. */
    public void requireWriteAccess(User user, int accountId) throws TestScriptException {
        if (DashboardMode.isSaasDeployment() && !(isAktoUser(user) || isWhiteListedAccount(accountId))) {
            throw new TestScriptException("Scripts are not enabled for this account");
        }
    }

    static boolean isAktoUser(User user) {
        return user != null && user.getLogin() != null && user.getLogin().contains("@akto.io");
    }

    static boolean isWhiteListedAccount(int accountId) {
        return WHITE_LISTED_ACCOUNTS.contains(accountId);
    }

    private TestScript requireExisting(String id) throws TestScriptException {
        if (id == null || id.isEmpty()) {
            throw new TestScriptException("Script id is required");
        }
        TestScript script = TestScriptsDao.instance.findById(id);
        if (script == null) {
            throw notFound();
        }
        return script;
    }

    private static void requireLogin(String login) throws TestScriptException {
        if (login == null || login.isEmpty()) {
            throw new TestScriptException("Unknown user");
        }
    }

    private static TestScriptException notFound() {
        return new TestScriptException("Script not found");
    }

    private static TestScriptException nameTaken(String name) {
        return new TestScriptException("A script named '" + name + "' already exists");
    }
}
