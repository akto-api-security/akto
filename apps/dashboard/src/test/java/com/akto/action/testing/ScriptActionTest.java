package com.akto.action.testing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mockStatic;

import java.util.HashMap;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;

import com.akto.MongoBasedTest;
import com.akto.dao.context.Context;
import com.akto.dao.testing.TestingRunConfigDao;
import com.akto.dao.testing.config.TestScriptsDao;
import com.akto.dto.User;
import com.akto.dto.testing.config.TestScript;
import com.akto.util.DashboardMode;

public class ScriptActionTest extends MongoBasedTest {

    private static final int WHITE_LISTED_ACCOUNT_ID = 1764738582;

    @Before
    public void setUp() {
        Context.accountId.set(ACCOUNT_ID);
        TestScriptsDao.instance.getMCollection().drop();
        TestingRunConfigDao.instance.getMCollection().drop();
        TestScriptsDao.instance.createIndicesIfAbsent();
    }

    @After
    public void tearDown() {
        Context.accountId.set(ACCOUNT_ID);
    }

    private ScriptAction createAction(String login) {
        ScriptAction action = new ScriptAction();
        Map<String, Object> session = new HashMap<>();
        User user = new User();
        user.setLogin(login);
        session.put("user", user);
        action.setSession(session);
        return action;
    }

    private ScriptAction addAction(String login, String name, String type, String js) {
        ScriptAction action = createAction(login);
        action.setName(name);
        action.setScriptType(type);
        action.setJavascript(js);
        return action;
    }

    @Test
    public void testAddScript_blocksNonAktoNonWhitelistedOnSaas() {
        try (MockedStatic<DashboardMode> dashboardModeMock = mockStatic(DashboardMode.class)) {
            dashboardModeMock.when(DashboardMode::isSaasDeployment).thenReturn(true);
            Context.accountId.set(1);
            ScriptAction action = addAction("test@notakto.com", "s", "PRE_REQUEST", "x();");
            assertEquals("ERROR", action.addScript());
            assertTrue(action.getActionErrors().iterator().next().contains("not enabled"));
        }
    }

    @Test
    public void testAddScript_allowsWhitelistedAccountOnSaas() {
        try (MockedStatic<DashboardMode> dashboardModeMock = mockStatic(DashboardMode.class)) {
            dashboardModeMock.when(DashboardMode::isSaasDeployment).thenReturn(true);
            Context.accountId.set(WHITE_LISTED_ACCOUNT_ID);
            TestScriptsDao.instance.getMCollection().drop();
            ScriptAction action = addAction("test@notakto.com", "s", "PRE_REQUEST", "x();");
            assertEquals("SUCCESS", action.addScript());
        }
    }

    @Test
    public void testAddScript_allowsNonWhitelistedAktoAccountOnSaas() {
        try (MockedStatic<DashboardMode> dashboardModeMock = mockStatic(DashboardMode.class)) {
            dashboardModeMock.when(DashboardMode::isSaasDeployment).thenReturn(true);
            Context.accountId.set(1000000);
            TestScriptsDao.instance.getMCollection().drop();
            ScriptAction action = addAction("test@akto.io", "s", "PRE_REQUEST", "x();");
            assertEquals("SUCCESS", action.addScript());
        }
    }

    private static com.akto.service.scripts.validation.ScriptValidationRequest validationRequest(String validationId) {
        com.akto.service.scripts.validation.ScriptValidationRequest r = new com.akto.service.scripts.validation.ScriptValidationRequest();
        r.setValidationId(validationId);
        r.setApiCollectionId(1);
        r.setUrl("https://a.test/x");
        r.setMethod("POST");
        r.setPostJavascript("body = 1;");
        return r;
    }

    @Test
    public void testValidateTestScript_blocksNonAktoNonWhitelistedOnSaas() {
        try (MockedStatic<DashboardMode> dashboardModeMock = mockStatic(DashboardMode.class)) {
            dashboardModeMock.when(DashboardMode::isSaasDeployment).thenReturn(true);
            Context.accountId.set(1);
            ScriptAction action = createAction("test@notakto.com");
            action.setValidationRequest(validationRequest(java.util.UUID.randomUUID().toString()));
            assertEquals("ERROR", action.validateTestScript());
            assertTrue(action.getActionErrors().iterator().next().contains("not enabled"));
            assertNull(action.getScriptValidationResult());
        }
    }

    @Test
    public void testValidationInputErrorsAreRejections() {
        ScriptAction bad = createAction("a@akto.io");
        bad.setValidationRequest(validationRequest("not-a-uuid"));
        assertEquals("ERROR", bad.validateTestScript());
        assertTrue(bad.getActionErrors().iterator().next().contains("UUID"));

        ScriptAction missing = createAction("a@akto.io");
        assertEquals("ERROR", missing.validateTestScript());
        assertTrue(missing.getActionErrors().iterator().next().contains("required"));
    }

    @Test
    public void testMiniTestingRequestsAreScheduledNotRunInline() {
        com.akto.service.scripts.validation.ScriptValidationRequest request = validationRequest(java.util.UUID.randomUUID().toString());
        request.setMiniTestingName("no-such-module");
        ScriptAction action = createAction("a@akto.io");
        action.setValidationRequest(request);

        assertEquals("ERROR", action.validateTestScript());
        // the mini-testing path answered (a dashboard replay would have said "No captured request")
        assertTrue(action.getActionErrors().iterator().next().contains("Mini-testing"));
        assertNull(action.getScriptValidationResult());
        assertNull(action.getScriptValidationPlaygroundId());
    }

    @Test
    public void testFetchScriptValidationRejectsUnknownAndMalformedIds() {
        ScriptAction unknown = createAction("a@akto.io");
        unknown.setScriptValidationPlaygroundId(new org.bson.types.ObjectId().toHexString());
        assertEquals("ERROR", unknown.fetchScriptValidation());
        assertTrue(unknown.getActionErrors().iterator().next().contains("not found"));
        assertNull(unknown.getScriptValidationResult());

        ScriptAction malformed = createAction("a@akto.io");
        malformed.setScriptValidationPlaygroundId("nope");
        assertEquals("ERROR", malformed.fetchScriptValidation());
        assertTrue(malformed.getActionErrors().iterator().next().contains("Invalid validation id"));
    }

    @Test
    public void testFetchScriptValidation_blocksNonAktoNonWhitelistedOnSaas() {
        try (MockedStatic<DashboardMode> dashboardModeMock = mockStatic(DashboardMode.class)) {
            dashboardModeMock.when(DashboardMode::isSaasDeployment).thenReturn(true);
            Context.accountId.set(1);
            ScriptAction action = createAction("test@notakto.com");
            action.setScriptValidationPlaygroundId(new org.bson.types.ObjectId().toHexString());
            assertEquals("ERROR", action.fetchScriptValidation());
            assertTrue(action.getActionErrors().iterator().next().contains("not enabled"));
        }
    }

    @Test
    public void testCrudFlow() {
        ScriptAction add = addAction("a@akto.io", "sign", "PRE_REQUEST", "pre();");
        assertEquals("SUCCESS", add.addScript());
        String id = add.getTestScript().getId();

        ScriptAction list = createAction("a@akto.io");
        assertEquals("SUCCESS", list.fetchAllScripts());
        assertEquals(1, list.getScripts().size());
        assertEquals("sign", list.getScripts().get(0).getName());

        ScriptAction fetch = createAction("a@akto.io");
        fetch.setScriptId(id);
        assertEquals("SUCCESS", fetch.fetchScript());
        assertEquals("pre();", fetch.getTestScript().getJavascript());

        ScriptAction update = createAction("b@akto.io");
        update.setScriptId(id);
        update.setJavascript("changed();");
        assertEquals("SUCCESS", update.updateScript());
        assertEquals("b@akto.io", update.getTestScript().getLastUpdatedBy());
        assertEquals("a@akto.io", update.getTestScript().getCreatedBy());

        ScriptAction delete = createAction("a@akto.io");
        delete.setScriptId(id);
        assertEquals("SUCCESS", delete.deleteScript());
        assertNull(TestScriptsDao.instance.findById(id));
    }

    @Test
    public void testRejectionsReturnErrorWithMessage() {
        assertEquals("SUCCESS", addAction("a@akto.io", "sign", "PRE_REQUEST", "pre();").addScript());

        ScriptAction dup = addAction("a@akto.io", "sign", "POST_REQUEST", "post();");
        assertEquals("ERROR", dup.addScript());
        assertTrue(dup.getActionErrors().iterator().next().contains("already exists"));

        assertEquals("ERROR", addAction("a@akto.io", "x", "PRE_TEST", "x();").addScript());
        assertEquals("ERROR", addAction("a@akto.io", "x", "PRE_REQUEST", null).addScript());

        ScriptAction missing = createAction("a@akto.io");
        missing.setScriptId("nope");
        assertEquals("ERROR", missing.fetchScript());

        ScriptAction updateMissing = createAction("a@akto.io");
        updateMissing.setScriptId("nope");
        updateMissing.setJavascript("x();");
        assertEquals("ERROR", updateMissing.updateScript());
        assertEquals(1, TestScriptsDao.instance.findAllSummaries().size());
    }

    @Test
    public void testDefaultScriptCannotBeDeleted() {
        ScriptAction add = addAction("a@akto.io", TestScript.DEFAULT_PRE_NAME, "PRE_REQUEST", "pre();");
        assertEquals("SUCCESS", add.addScript());

        ScriptAction delete = createAction("a@akto.io");
        delete.setScriptId(add.getTestScript().getId());
        assertEquals("ERROR", delete.deleteScript());
    }
}
