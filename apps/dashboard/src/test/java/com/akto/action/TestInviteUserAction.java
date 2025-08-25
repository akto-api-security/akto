package com.akto.action;

import org.junit.Test;

import com.akto.MongoBasedTest;
import com.akto.dao.CustomRoleDao;
import com.akto.dao.RBACDao;
import com.akto.dao.UsersDao;
import com.akto.dto.CustomRole;
import com.akto.dto.RBAC;
import com.akto.dto.User;
import com.akto.dto.UserAccountEntry;
import com.akto.dto.RBAC.Role;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class TestInviteUserAction extends MongoBasedTest{

    @Test
    public void testValidateEmail() {
        InviteUserAction.commonOrganisationsMap.put("child1.com", "parent");
        InviteUserAction.commonOrganisationsMap.put("child2.com", "parent");

        String code = InviteUserAction.validateEmail("ankush@akto.io", "avneesh@akto.io");
        assertNull(code);

        code = InviteUserAction.validateEmail("https://app.akto.io/signup?signupInvitationCode=eyJhbGciOiJSUzI1NiJ9.eyJpc3MiOiJBa3RvIiwic3ViIjoiaW52aXRlX3VzZXIiLCJlbWFpbCI6InVtZXNoKzFAYWt0by5pbyIsImlhdCI6MTc0OTQ1MjQ0MCwiZXhwIjoxNzUwMDU3MjQwfQ.A1wo7Pkg-6X1xvmV7LpJ-yQD5Y2nse3sthtn3sJT0v2-Osb2Hqr85SRMIiZOut6Srp3q1waDzqNpVNC0rdkiQKAGKPUR_hrtv44CcUGxnAv8jJ_1qICg8APHq_ZbhgiwdqlBtVxr-Q1cUA0TiM6yQJRNcXjpm8CWrpwHcOLmjNntAecvfCG50pVtDgiwRXnMKK8tkw-pvQaPyf-mkY9Dp43NNJ2vkocndVXEP4aIMWaK5fWnlvWZrTB5hOKDy4gEa8jzFChDfU2yB5U-CcNM79ZWTLLBf6ZPFIc9PCmQm6cxkuzBRgRvjofceLf2QaSuS2sso11E59hrGaDczd847A&signupEmailId=umesh+2@aktao.io", "avneesh@aktao.io");
        assertEquals(code, InviteUserAction.INVALID_EMAIL_ERROR);

        code = InviteUserAction.validateEmail(null, "avneesh@akto.io");
        assertEquals(code, InviteUserAction.INVALID_EMAIL_ERROR);

        code = InviteUserAction.validateEmail("ankita", "avneesh@akto.io");
        assertEquals(code, InviteUserAction.INVALID_EMAIL_ERROR);

        code = InviteUserAction.validateEmail("anuj@amazon.com", "avneesh@gmail.io");
        assertEquals(code, InviteUserAction.DIFFERENT_ORG_EMAIL_ERROR);

        code = InviteUserAction.validateEmail("jim@akto.com", "avneesh@gmail.io");
        assertEquals(code, InviteUserAction.DIFFERENT_ORG_EMAIL_ERROR);

        code = InviteUserAction.validateEmail("avneesh@akto.io", "aryan@bigcorp.com");
        assertNull(code);

        code = InviteUserAction.validateEmail("avneesh@child1.com", "aryan@child2.com");
        assertNull(code);

        code = InviteUserAction.validateEmail("avneesh@child1.com", "aryan@akto.io");
        assertEquals(code, InviteUserAction.DIFFERENT_ORG_EMAIL_ERROR);

        code = InviteUserAction.validateEmail("avneesh@akto.io", "aryan@child2.com");
        assertNull(code);

        code = InviteUserAction.validateEmail("avneesh@bigcorp.com", "ankush@bigcorp.com");
        assertNull(code);

        code = InviteUserAction.validateEmail("avneesh@bigcorp.com", "ankush@bigcorp2.com");
        assertEquals(code, InviteUserAction.DIFFERENT_ORG_EMAIL_ERROR);

        InviteUserAction.commonOrganisationsMap = new HashMap<>();
    }

    @Test
    public void testInviteUserAction() {
        RBACDao.instance.deleteAll(new BasicDBObject());
        UsersDao.instance.deleteAll(new BasicDBObject());
        CustomRoleDao.instance.deleteAll(new BasicDBObject());
        InviteUserAction inviteUserAction = new InviteUserAction();

        UserAccountEntry userAccountEntry = new UserAccountEntry();
        userAccountEntry.setAccountId(ACCOUNT_ID);
        userAccountEntry.setDefault(true);
        Map<String, UserAccountEntry> accountAccessMap = new HashMap<>();
        accountAccessMap.put(ACCOUNT_ID+"", userAccountEntry);

        Map<String, Object> session = new HashMap<>();
        UsersDao.instance.insertOne(new User("test", "test@akto.io", accountAccessMap, null));
        User user = UsersDao.instance.findOne(new BasicDBObject());
        session.put("user", user);
        inviteUserAction.setSession(session);

        inviteUserAction.setInviteeEmail("dude@akto.io");
        inviteUserAction.setInviteeRole(Role.DEVELOPER.name());

        RBACDao.instance.insertOne(new RBAC(user.getId(), RBAC.Role.MEMBER.name(), ACCOUNT_ID));

        assertEquals("SUCCESS",inviteUserAction.execute());

        inviteUserAction.setInviteeRole(Role.ADMIN.name());

        assertEquals("ERROR",inviteUserAction.execute());

        CustomRole customRole = new CustomRole("CUSTOM_ROLE", Role.ADMIN.name(), new ArrayList<>(), false, new ArrayList<>());
        CustomRoleDao.instance.insertOne(customRole);

        inviteUserAction.setInviteeRole("CUSTOM_ROLE");

        assertEquals("ERROR",inviteUserAction.execute());

        CustomRoleDao.instance.updateOne(Filters.eq(CustomRole._NAME, customRole.getName()), Updates.set(CustomRole.BASE_ROLE, Role.MEMBER.name()));

        assertEquals("SUCCESS",inviteUserAction.execute());


    }

}
