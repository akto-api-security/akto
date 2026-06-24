package com.akto.action.testing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mockStatic;

import java.util.HashMap;
import java.util.Map;

import org.junit.After;
import org.junit.Test;
import org.mockito.MockedStatic;

import com.akto.MongoBasedTest;
import com.akto.dao.context.Context;
import com.akto.dao.testing.config.TestScriptsDao;
import com.akto.dto.User;
import com.akto.dto.testing.config.TestScript;
import com.akto.util.DashboardMode;

public class ScriptActionTest extends MongoBasedTest {

    private static final int WHITE_LISTED_ACCOUNT_ID = 1764738582;

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

    private TestScript validTestScript() {
        TestScript script = new TestScript();
        script.setJavascript("console.log('test');");
        return script;
    }

    @Test
    public void testIsWhiteListedAccount_returnsTrueForWhitelistedId() {
        Context.accountId.set(WHITE_LISTED_ACCOUNT_ID);
        ScriptAction action = new ScriptAction();
        assertTrue(action.isWhiteListedAccount());
    }

    @Test
    public void testIsWhiteListedAccount_returnsFalseForOtherId() {
        Context.accountId.set(1);
        ScriptAction action = new ScriptAction();
        assertFalse(action.isWhiteListedAccount());
    }

    @Test
    public void testAddScript_blocksNonAktoNonWhitelistedOnSaas() {
        try (MockedStatic<DashboardMode> dashboardModeMock = mockStatic(DashboardMode.class)) {
            dashboardModeMock.when(DashboardMode::isSaasDeployment).thenReturn(true);

            Context.accountId.set(1);
            ScriptAction action = createAction("test@notakto.com");
            action.setTestScript(validTestScript());

            assertEquals("ERROR", action.addScript());
        }
    }

    @Test
    public void testAddScript_allowsWhitelistedAccountOnSaas() {
        try (MockedStatic<DashboardMode> dashboardModeMock = mockStatic(DashboardMode.class)) {
            dashboardModeMock.when(DashboardMode::isSaasDeployment).thenReturn(true);

            TestScriptsDao.instance.getMCollection().drop();
            Context.accountId.set(WHITE_LISTED_ACCOUNT_ID);
            ScriptAction action = createAction("test@notakto.com");
            action.setTestScript(validTestScript());

            assertEquals("SUCCESS", action.addScript());
        }
    }

    @Test
    public void testAddScript_allowsNonWhitelistedAktoAccountOnSaas() {
        try (MockedStatic<DashboardMode> dashboardModeMock = mockStatic(DashboardMode.class)) {
            dashboardModeMock.when(DashboardMode::isSaasDeployment).thenReturn(true);

            TestScriptsDao.instance.getMCollection().drop();
            Context.accountId.set(1000000);
            ScriptAction action = createAction("test@akto.io");
            action.setTestScript(validTestScript());

            assertEquals("SUCCESS", action.addScript());
        }
    }
}
