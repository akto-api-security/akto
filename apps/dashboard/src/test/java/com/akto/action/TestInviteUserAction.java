package com.akto.action;

import org.junit.Test;

import java.util.HashMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class TestInviteUserAction {


    @Test
    public void testValidateEmail() {
        InviteUserAction.commonOrganisationsMap.put("child1.com", "parent");
        InviteUserAction.commonOrganisationsMap.put("child2.com", "parent");

        String code = InviteUserAction.validateEmail("ankush@akto.io", "avneesh@akto.io");
        assertNull(code);

        code = InviteUserAction.validateEmail(null, "avneesh@akto.io");
        assertEquals(code, InviteUserAction.INVALID_EMAIL_ERROR);

        code = InviteUserAction.validateEmail("ankita", "avneesh@akto.io");
        assertEquals(code, InviteUserAction.INVALID_EMAIL_ERROR);

        code = InviteUserAction.validateEmail("anuj@amazon.com", "avneesh@gmail.io");
        assertEquals(code, InviteUserAction.DIFFERENT_ORG_EMAIL_ERROR);

        code = InviteUserAction.validateEmail("jim@akto", "avneesh@gmail.io");
        assertEquals(code, InviteUserAction.DIFFERENT_ORG_EMAIL_ERROR);

        code = InviteUserAction.validateEmail("avneesh@akto.io", "aryan@bigcorp.com");
        assertNull(code);

        code = InviteUserAction.validateEmail("avneesh@child1.com", "aryan@child2.com");
        assertNull(code);

        code = InviteUserAction.validateEmail("avneesh@child1.com", "aryan@akto.io");
        assertEquals(code, InviteUserAction.DIFFERENT_ORG_EMAIL_ERROR);

        code = InviteUserAction.validateEmail("avneesh@akto.io", "aryan@child2.com");
        assertNull(code);

        InviteUserAction.commonOrganisationsMap = new HashMap<>();
    }
}
