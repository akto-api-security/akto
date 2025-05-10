package com.akto.action;

import static org.junit.Assert.assertEquals;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.akto.MongoBasedTest;
import com.akto.dao.RBACDao;
import com.akto.dao.UsersDao;
import com.akto.dao.context.Context;
import com.akto.dto.RBAC;
import com.akto.dto.RBAC.Role;
import com.akto.dto.User;
import com.akto.dto.UserAccountEntry;
import com.mongodb.BasicDBObject;

public class TestAccountAction extends MongoBasedTest {

    @Test
    public void testInitializeAccount() {
        int testAccount = ACCOUNT_ID + 100;
        Context.accountId.set(testAccount);
        String email = "test@akto.io";
        int newAccountId = 12390;
        String newAccountName = "NEW_ACCOUNT";
        Map<String, UserAccountEntry> accounts = new HashMap<>();
        accounts.put(testAccount + "", new UserAccountEntry(testAccount));
        UsersDao.instance.deleteAll(new BasicDBObject());
        RBACDao.instance.deleteAll(new BasicDBObject());
        UsersDao.instance.insertOne(new User("test", "test@akto.io", accounts, null));
        User user = UsersDao.instance.findOne(new BasicDBObject());
        RBACDao.instance.insertOne(new RBAC(user.getId(), RBAC.Role.ADMIN.name(), testAccount));
        List<RBAC> rbacList = RBACDao.instance.findAll(new BasicDBObject());

        assertEquals(1, user.getAccounts().size());
        assertEquals(1, rbacList.size());

        AccountAction.initializeAccount(email, newAccountId, newAccountName, false, RBAC.Role.ADMIN.name());

        user = UsersDao.instance.findOne(new BasicDBObject());
        assertEquals(2, user.getAccounts().size());
        rbacList = RBACDao.instance.findAll(new BasicDBObject());
        assertEquals(2, rbacList.size());

        Role role = RBACDao.getCurrentRoleForUser(user.getId(), newAccountId);
        assertEquals(Role.ADMIN, role);
        role = RBACDao.getCurrentRoleForUser(user.getId(), testAccount);
        assertEquals(Role.ADMIN, role);

        
    }

}
