package com.akto.dto;

import static org.junit.Assert.assertEquals;

import java.util.Collections;

import org.junit.Test;

import com.akto.dto.RBAC.Role;

public class TestCustomRole {

    private static CustomRole withBase(String baseRole) {
        return new CustomRole("custom", baseRole, Collections.emptyList(), false, false, Collections.emptyList());
    }

    @Test
    public void everyAllowedBaseRoleIsKeptAndReadsBack() {
        // Readers do Role.valueOf(customRole.getBaseRole()), so the stored value must be the enum name.
        for (Role role : new Role[]{Role.ADMIN, Role.DEVELOPER, Role.MEMBER, Role.GUEST, Role.THREAT_ENGINEER, Role.THREAT_VIEWER}) {
            assertEquals(role, Role.valueOf(withBase(role.name()).getBaseRole()));
        }
    }

    @Test
    public void anythingElseFallsBackToGuest() {
        assertEquals("GUEST", withBase("NOT_A_ROLE").getBaseRole());
        assertEquals("GUEST", withBase(Role.NO_ACCESS.name()).getBaseRole());
    }
}
