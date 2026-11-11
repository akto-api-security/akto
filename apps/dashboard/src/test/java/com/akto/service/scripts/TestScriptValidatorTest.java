package com.akto.service.scripts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

import com.akto.dto.testing.config.TestScript;

public class TestScriptValidatorTest {

    private final TestScriptValidator validator = new TestScriptValidator();

    private static String repeat(char c, int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) sb.append(c);
        return sb.toString();
    }

    @Test
    public void typeAcceptsOnlyLibraryTypes() throws Exception {
        assertEquals(TestScript.Type.PRE_REQUEST, validator.validateType("PRE_REQUEST"));
        assertEquals(TestScript.Type.POST_REQUEST, validator.validateType("POST_REQUEST"));
        assertThrows(TestScriptException.class, () -> validator.validateType("PRE_TEST"));
        assertThrows(TestScriptException.class, () -> validator.validateType("pre_request"));
        assertThrows(TestScriptException.class, () -> validator.validateType(""));
        assertThrows(TestScriptException.class, () -> validator.validateType(null));
    }

    @Test
    public void nameIsTrimmedAndCapped() throws Exception {
        assertEquals("sign hmac", validator.validateName("  sign hmac ", TestScript.Type.PRE_REQUEST));
        String max = repeat('a', TestScriptValidator.MAX_NAME_LENGTH);
        assertEquals(max, validator.validateName(max, TestScript.Type.PRE_REQUEST));
        assertThrows(TestScriptException.class, () -> validator.validateName(max + "a", TestScript.Type.PRE_REQUEST));
        assertThrows(TestScriptException.class, () -> validator.validateName("   ", TestScript.Type.PRE_REQUEST));
        assertThrows(TestScriptException.class, () -> validator.validateName(null, TestScript.Type.PRE_REQUEST));
    }

    @Test
    public void nameRejectsUnsafeCharacters() {
        assertThrows(TestScriptException.class, () -> validator.validateName("<script>", TestScript.Type.PRE_REQUEST));
        assertThrows(TestScriptException.class, () -> validator.validateName("-leading-dash", TestScript.Type.PRE_REQUEST));
        assertThrows(TestScriptException.class, () -> validator.validateName("a/b", TestScript.Type.PRE_REQUEST));
    }

    @Test
    public void defaultNameMustMatchItsType() throws Exception {
        assertEquals("default-test-pre", validator.validateName("default-test-pre", TestScript.Type.PRE_REQUEST));
        assertEquals("default-test-post", validator.validateName("default-test-post", TestScript.Type.POST_REQUEST));
        assertThrows(TestScriptException.class, () -> validator.validateName("default-test-pre", TestScript.Type.POST_REQUEST));
        assertThrows(TestScriptException.class, () -> validator.validateName("default-test-post", TestScript.Type.PRE_REQUEST));
    }

    @Test
    public void javascriptRequiredAndCappedInBytes() throws Exception {
        assertThrows(TestScriptException.class, () -> validator.validateJavascript(null));
        assertThrows(TestScriptException.class, () -> validator.validateJavascript("  \n"));

        String atCap = repeat('a', TestScriptValidator.MAX_JAVASCRIPT_BYTES);
        assertEquals(atCap, validator.validateJavascript(atCap));
        assertThrows(TestScriptException.class, () -> validator.validateJavascript(atCap + "a"));

        // multi-byte characters count by encoded size, not by char count
        String multiByte = repeat('é', TestScriptValidator.MAX_JAVASCRIPT_BYTES / 2 + 1);
        assertThrows(TestScriptException.class, () -> validator.validateJavascript(multiByte));
    }
}
