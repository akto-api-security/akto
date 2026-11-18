package com.akto.dto.testing.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public class ScriptSelectionTest {

    @Test
    public void nullMeansDefault() {
        ScriptSelection s = ScriptSelection.from(null, TestScript.Type.PRE_REQUEST);
        assertEquals(ScriptSelection.Kind.DEFAULT, s.getKind());
        assertEquals(TestScript.Type.PRE_REQUEST, s.getType());
        assertNull(s.getScriptId());
    }

    @Test
    public void emptyStringMeansNone() {
        ScriptSelection s = ScriptSelection.from(TestScript.NO_SCRIPT, TestScript.Type.POST_REQUEST);
        assertEquals(ScriptSelection.Kind.NONE, s.getKind());
        assertNull(s.getScriptId());
    }

    @Test
    public void anyOtherValueIsById() {
        ScriptSelection s = ScriptSelection.from("abc-123", TestScript.Type.POST_REQUEST);
        assertEquals(ScriptSelection.Kind.BY_ID, s.getKind());
        assertEquals("abc-123", s.getScriptId());
    }

    @Test
    public void whitespaceIdIsNotTreatedAsNoneOrDefault() {
        // the validator rejects it at the API boundary; at runtime it must not silently become "no script"
        ScriptSelection s = ScriptSelection.from(" ", TestScript.Type.PRE_REQUEST);
        assertEquals(ScriptSelection.Kind.BY_ID, s.getKind());
        assertEquals(" ", s.getScriptId());
    }

    @Test
    public void unsupportedTypeIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> ScriptSelection.from(null, TestScript.Type.PRE_TEST));
        assertThrows(IllegalArgumentException.class, () -> ScriptSelection.from(null, null));
    }
}
