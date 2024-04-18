package com.akto.action;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class TestInviteUserAction {


    @Test
    public void testValidateEmail() {
        String code = InviteUserAction.validateEmail("ankush@akto.io", "avneesh@akto.io");
        assertNull(code);

        code = InviteUserAction.validateEmail(null, "avneesh@akto.io");
        assertEquals(code, InviteUserAction.INVALID_EMAIL_ERROR);

        code = InviteUserAction.validateEmail("ankita", "avneesh@akto.io");
        assertEquals(code, InviteUserAction.INVALID_EMAIL_ERROR);

        code = InviteUserAction.validateEmail("anuj@amazon.com", "avneesh@akto.io");
        assertEquals(code, InviteUserAction.DIFFERENT_ORG_EMAIL_ERROR);

        code = InviteUserAction.validateEmail("jim@akto", "avneesh@akto.io");
        assertEquals(code, InviteUserAction.DIFFERENT_ORG_EMAIL_ERROR);
    }
}
