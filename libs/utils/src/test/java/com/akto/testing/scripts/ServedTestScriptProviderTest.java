package com.akto.testing.scripts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import com.akto.dto.testing.config.TestScript;

/** The chain the database-abstractor serves to mini-testing: library, then drafts (switchable), then legacy. */
public class ServedTestScriptProviderTest {

    private static final String DRAFT_ID = "ai-draft-3f2a-pre";

    private final StubTestScriptProvider library = new StubTestScriptProvider();
    private final StubTestScriptProvider drafts = new StubTestScriptProvider();
    private final StubTestScriptProvider legacy = new StubTestScriptProvider();
    private final AtomicBoolean draftsOn = new AtomicBoolean(true);
    private final TestScriptProvider served = TestScriptProviders.served(library, drafts, legacy, draftsOn::get);

    @Test
    public void aDraftIsServedWhenTheLibraryHasNothingForItsId() {
        drafts.byId.put(DRAFT_ID, StubTestScriptProvider.script(DRAFT_ID, TestScript.Type.PRE_REQUEST, "draft();"));

        assertEquals("draft();", served.findById(DRAFT_ID).getJavascript());
        assertEquals(0, legacy.byIdCalls);
    }

    @Test
    public void aLibraryScriptIsServedWithoutAskingForDrafts() {
        library.byId.put("lib-1", StubTestScriptProvider.script("lib-1", TestScript.Type.POST_REQUEST, "lib();"));

        assertEquals("lib();", served.findById("lib-1").getJavascript());
        assertEquals(0, drafts.byIdCalls);
    }

    @Test
    public void switchedOffNeverTouchesDraftsAndAnswersNotFound() {
        drafts.byId.put(DRAFT_ID, StubTestScriptProvider.script(DRAFT_ID, TestScript.Type.PRE_REQUEST, "draft();"));
        draftsOn.set(false);

        assertNull(served.findById(DRAFT_ID));
        assertEquals(0, drafts.byIdCalls, "a switched-off source is not queried at all");
        assertEquals(1, legacy.byIdCalls, "the chain carries on to the legacy store");
    }

    @Test
    public void switchedOffLeavesLibraryAndLegacyScriptsServing() {
        library.byId.put("lib-1", StubTestScriptProvider.script("lib-1", TestScript.Type.PRE_REQUEST, "lib();"));
        legacy.defaults.put(TestScript.Type.POST_REQUEST, StubTestScriptProvider.script("legacy", TestScript.Type.POST_REQUEST, "old();"));
        draftsOn.set(false);

        assertEquals("lib();", served.findById("lib-1").getJavascript());
        assertEquals("old();", served.findDefault(TestScript.Type.POST_REQUEST).getJavascript());
    }

    @Test
    public void theSwitchIsReadOnEveryLookupSoItCanBeFlippedWithoutARestart() {
        drafts.byId.put(DRAFT_ID, StubTestScriptProvider.script(DRAFT_ID, TestScript.Type.PRE_REQUEST, "draft();"));

        draftsOn.set(false);
        assertNull(served.findById(DRAFT_ID));
        draftsOn.set(true);
        assertEquals("draft();", served.findById(DRAFT_ID).getJavascript());
    }

    @Test
    public void aDraftsOutageIsNotHiddenAsNotFound() {
        drafts.failWith = new IllegalStateException("mongo down");

        assertThrows(IllegalStateException.class, () -> served.findById(DRAFT_ID));
    }

    @Test
    public void draftsAreServedUnlessTheEnvValueIsExplicitlyFalse() {
        assertTrue(TestScriptProviders.draftsServed(null));
        assertTrue(TestScriptProviders.draftsServed(""));
        assertTrue(TestScriptProviders.draftsServed("true"));
        assertTrue(TestScriptProviders.draftsServed("garbage"));
        assertFalse(TestScriptProviders.draftsServed("false"));
        assertFalse(TestScriptProviders.draftsServed(" FALSE "));
    }

    @Test
    public void thereIsNoDefaultDraft() {
        assertNull(new DraftTestScriptProvider().findDefault(TestScript.Type.PRE_REQUEST));
        assertNull(new DraftTestScriptProvider().findDefault(TestScript.Type.POST_REQUEST));
    }
}
