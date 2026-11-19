package com.akto.dto.testing.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TestScriptTest {

    @Test
    public void untypedRequestMeansPreRequest() {
        assertEquals(TestScript.Type.PRE_REQUEST, TestScript.typeForUntypedRequest(null));
        assertEquals(TestScript.Type.PRE_REQUEST, TestScript.typeForUntypedRequest(TestScript.Type.PRE_REQUEST));
        assertEquals(TestScript.Type.POST_REQUEST, TestScript.typeForUntypedRequest(TestScript.Type.POST_REQUEST));
    }

    @Test
    public void defaultNamesOnlyForLibraryTypes() {
        assertTrue(TestScript.isDefaultName(TestScript.defaultNameFor(TestScript.Type.PRE_REQUEST)));
        assertTrue(TestScript.isDefaultName(TestScript.defaultNameFor(TestScript.Type.POST_REQUEST)));
        assertFalse(TestScript.isDefaultName("sign-hmac"));
        assertThrows(IllegalArgumentException.class, () -> TestScript.defaultNameFor(TestScript.Type.PRE_TEST));
    }
}
