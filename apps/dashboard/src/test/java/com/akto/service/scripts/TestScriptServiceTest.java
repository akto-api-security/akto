package com.akto.service.scripts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mockStatic;

import java.util.List;

import org.bson.Document;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;

import com.akto.MongoBasedTest;
import com.akto.dao.context.Context;
import com.akto.dao.testing.TestingRunConfigDao;
import com.akto.dao.testing.config.TestScriptsDao;
import com.akto.dto.User;
import com.akto.dto.testing.TestingRunConfig;
import com.akto.dto.testing.config.TestScript;
import com.akto.dto.testing.config.TestScriptSummary;
import com.akto.util.DashboardMode;

public class TestScriptServiceTest extends MongoBasedTest {

    private final TestScriptService service = new TestScriptService();

    @Before
    public void setUp() {
        Context.accountId.set(ACCOUNT_ID);
        TestScriptsDao.instance.getMCollection().drop();
        TestingRunConfigDao.instance.getMCollection().drop();
        TestScriptsDao.instance.createIndicesIfAbsent();
        TestingRunConfigDao.instance.createIndicesIfAbsent();
    }

    private TestScript createPre(String name) throws TestScriptException {
        return service.create(name, "PRE_REQUEST", "pre();", "a@x.com");
    }

    @Test
    public void createStoresTrimmedNameAndAuthors() throws Exception {
        TestScript created = createPre("  sign ");
        TestScript read = TestScriptsDao.instance.findById(created.getId());
        assertEquals("sign", read.getName());
        assertEquals("a@x.com", read.getCreatedBy());
        assertEquals("a@x.com", read.getLastUpdatedBy());
        assertEquals(TestScript.Type.PRE_REQUEST, read.getType());
    }

    @Test
    public void duplicateNameIsRejectedWithoutSecondDoc() throws Exception {
        createPre("sign");
        TestScriptException e = assertThrows(TestScriptException.class,
            () -> service.create("sign", "POST_REQUEST", "post();", "b@x.com"));
        assertTrue(e.getMessage().contains("already exists"));
        assertEquals(1, TestScriptsDao.instance.count(new Document()));
    }

    @Test
    public void createRequiresLogin() {
        assertThrows(TestScriptException.class, () -> service.create("x", "PRE_REQUEST", "x();", null));
    }

    @Test
    public void listNeverReturnsBodies() throws Exception {
        createPre("one");
        List<TestScriptSummary> summaries = service.listSummaries();
        assertEquals(1, summaries.size());
        assertEquals("one", summaries.get(0).getName());
        // TestScriptSummary has no javascript field at all; the DAO projection also drops it
        assertNull(TestScriptsDao.instance.findAllSummaries().get(0).getJavascript());
    }

    @Test
    public void updateChangesBodyAndEditorOnly() throws Exception {
        TestScript created = createPre("sign");
        TestScript updated = service.updateJavascript(created.getId(), "changed();", "b@x.com");
        assertEquals("changed();", updated.getJavascript());
        assertEquals("sign", updated.getName());
        assertEquals("a@x.com", updated.getCreatedBy());
        assertEquals("b@x.com", updated.getLastUpdatedBy());
    }

    @Test
    public void updateOfUnknownIdNeverUpserts() {
        assertThrows(TestScriptException.class, () -> service.updateJavascript("missing", "x();", "a@x.com"));
        assertEquals(0, TestScriptsDao.instance.count(new Document()));
    }

    @Test
    public void defaultScriptCannotBeDeleted() throws Exception {
        TestScript def = createPre("default-test-pre");
        assertThrows(TestScriptException.class, () -> service.delete(def.getId()));
        assertTrue(TestScriptsDao.instance.findById(def.getId()) != null);
    }

    @Test
    public void referencedScriptCannotBeDeleted() throws Exception {
        TestScript s = createPre("sign");
        TestingRunConfig config = new TestingRunConfig();
        config.setId(7);
        config.setPreRequestScriptId(s.getId());
        TestingRunConfigDao.instance.insertOne(config);

        assertThrows(TestScriptException.class, () -> service.delete(s.getId()));

        TestingRunConfigDao.instance.getMCollection().drop();
        service.delete(s.getId());
        assertNull(TestScriptsDao.instance.findById(s.getId()));
    }

    @Test
    public void deleteOfUnknownIdIsRejected() {
        assertThrows(TestScriptException.class, () -> service.delete("missing"));
        assertThrows(TestScriptException.class, () -> service.delete(null));
    }

    @Test
    public void runSelectionAcceptsDefaultNoneAndMatchingId() throws Exception {
        TestScript pre = createPre("sign");
        service.validateRunSelection(null, TestScript.Type.PRE_REQUEST);
        service.validateRunSelection(TestScript.NO_SCRIPT, TestScript.Type.PRE_REQUEST);
        service.validateRunSelection(pre.getId(), TestScript.Type.PRE_REQUEST);
    }

    @Test
    public void runSelectionRejectsMissingOrWrongTypeId() throws Exception {
        TestScript pre = createPre("sign");
        assertThrows(TestScriptException.class, () -> service.validateRunSelection("missing", TestScript.Type.PRE_REQUEST));
        assertThrows(TestScriptException.class, () -> service.validateRunSelection(" ", TestScript.Type.PRE_REQUEST));
        assertThrows(TestScriptException.class, () -> service.validateRunSelection(pre.getId(), TestScript.Type.POST_REQUEST));
    }

    @Test
    public void aktoUserMatchesAktoLoginsOnly() {
        User user = new User();
        user.setLogin("test@akto.io");
        assertTrue(TestScriptService.isAktoUser(user));
        user.setLogin("test@notakto.com");
        assertFalse(TestScriptService.isAktoUser(user));
        user.setLogin(null);
        assertFalse(TestScriptService.isAktoUser(user));
        assertFalse(TestScriptService.isAktoUser(null));
    }

    @Test
    public void writeAccessGateUnchangedOnSaas() throws Exception {
        User other = new User();
        other.setLogin("test@notakto.com");
        User akto = new User();
        akto.setLogin("test@akto.io");

        try (MockedStatic<DashboardMode> mode = mockStatic(DashboardMode.class)) {
            mode.when(DashboardMode::isSaasDeployment).thenReturn(true);
            assertThrows(TestScriptException.class, () -> service.requireWriteAccess(other, 1));
            service.requireWriteAccess(other, 1764738582);
            service.requireWriteAccess(akto, 1);
            assertThrows(TestScriptException.class, () -> service.requireWriteAccess(null, 1));
        }
        try (MockedStatic<DashboardMode> mode = mockStatic(DashboardMode.class)) {
            mode.when(DashboardMode::isSaasDeployment).thenReturn(false);
            service.requireWriteAccess(other, 1);
        }
        assertTrue(TestScriptService.isWhiteListedAccount(1764738582));
        assertFalse(TestScriptService.isWhiteListedAccount(1));
    }
}
