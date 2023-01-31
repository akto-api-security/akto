package com.akto.action;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class TestSignupAction {

    @Test
    public void testValidatePassword() {
        String code = SignupAction.validatePassword("avneesh");
        assertEquals(code, SignupAction.MINIMUM_PASSWORD_ERROR);

        code = SignupAction.validatePassword("avneesh_avneesh_avneesh_avneesh_avneesh_avneesh_avneesh_avneesh_avneesh");
        assertEquals(code, SignupAction.MAXIMUM_PASSWORD_ERROR);

        code = SignupAction.validatePassword("avneesh\uD83D\uDE03123");
        assertEquals(code, SignupAction.INVALID_CHAR);

        code = SignupAction.validatePassword("avneesh_avneesh");
        assertEquals(code, SignupAction.MUST_BE_ALPHANUMERIC_ERROR);

        code = SignupAction.validatePassword("avneesh.12345");
        assertNull(code);

        code = SignupAction.validatePassword("hotavneesh1");
        assertNull(code);
    }
}
