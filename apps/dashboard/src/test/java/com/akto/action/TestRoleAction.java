package com.akto.action;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class TestRoleAction {

    @Test
    public void testValidRoleNames(){

        RoleAction roleAction = new RoleAction();

        roleAction.setRoleName("HELLO");
        assertEquals(true, roleAction.validateRoleName());

        roleAction.setRoleName("ADMIN");
        assertEquals(false, roleAction.validateRoleName());

        roleAction.setRoleName("DEVELOPER");
        assertEquals(false, roleAction.validateRoleName());

        roleAction.setRoleName("HELLO.WORLD");
        assertEquals(false, roleAction.validateRoleName());

        roleAction.setRoleName("HELLOworld");
        assertEquals(true, roleAction.validateRoleName());

        roleAction.setRoleName("HELLOworld123");
        assertEquals(true, roleAction.validateRoleName());

        roleAction.setRoleName("HELLO-world__ADMIN");
        assertEquals(true, roleAction.validateRoleName());
    }

}
