package com.akto.dao;

import com.akto.dao.context.Context;
import com.akto.dto.User;
import com.akto.dto.UserAccountEntry;
import com.akto.utils.MongoBasedTest;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

import java.util.HashMap;
import java.util.Map;

public class TestUsersDao extends MongoBasedTest {

    @Test
    public void testGetFirstUser() {
        UsersDao.instance.getMCollection().drop();
        Map<String, UserAccountEntry> accounts = new HashMap<>();
        accounts.put(ACCOUNT_ID+"", new UserAccountEntry(ACCOUNT_ID));
        UsersDao.instance.insertOne(new User("avneesh", "avneesh@akto.io", accounts,null, null));

        User firstUser = UsersDao.instance.getFirstUser(ACCOUNT_ID);
        assertEquals(firstUser.getLogin(), "avneesh@akto.io");

        User secondUser = new User("ankush", "ankush@akto.io", accounts,null,null);
        secondUser.setId(Context.now()+1000);
        UsersDao.instance.insertOne(secondUser);
        firstUser = UsersDao.instance.getFirstUser(ACCOUNT_ID);
        assertEquals(firstUser.getLogin(), "avneesh@akto.io");
    }
}
