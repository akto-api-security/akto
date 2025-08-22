package com.akto.action;

import org.junit.Test;

import com.akto.MongoBasedTest;
import com.akto.dao.CustomRoleDao;
import com.akto.dto.CustomRole;
import com.akto.dto.RBAC.Role;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.ArrayList;

public class TestSignupAction extends MongoBasedTest{

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

    @Test
    public void testFetchDefaultInviteRole(){
        CustomRoleDao.instance.deleteAll(new BasicDBObject());
        
        SignupAction signupAction = new SignupAction();

        assertEquals("GUEST", signupAction.fetchDefaultInviteRole(ACCOUNT_ID, "GUEST"));

        CustomRole customRole = new CustomRole("CUSTOM_ROLE", Role.ADMIN.name(), new ArrayList<>(), false, new ArrayList<>());
        CustomRoleDao.instance.insertOne(customRole);

        assertEquals("GUEST", signupAction.fetchDefaultInviteRole(ACCOUNT_ID, "GUEST"));

        CustomRoleDao.instance.updateOne(Filters.eq(CustomRole._NAME, customRole.getName()), Updates.set(CustomRole.DEFAULT_INVITE_ROLE, true));

        assertEquals(customRole.getName(), signupAction.fetchDefaultInviteRole(ACCOUNT_ID, "GUEST"));

    }
}
