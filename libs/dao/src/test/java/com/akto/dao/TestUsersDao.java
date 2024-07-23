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
        UsersDao.instance.insertOne(new User("avneesh", "avneesh@akto.io", accounts,null));

        User firstUser = UsersDao.instance.getFirstUser(ACCOUNT_ID);
        assertEquals(firstUser.getLogin(), "avneesh@akto.io");

        User secondUser = new User("ankush", "ankush@akto.io", accounts,null);
        secondUser.setId(Context.now()+1000);
        UsersDao.instance.insertOne(secondUser);
        firstUser = UsersDao.instance.getFirstUser(ACCOUNT_ID);
        assertEquals(firstUser.getLogin(), "avneesh@akto.io");

        User thirdUser = new User("aryan", "aryan@bigcorp.com", accounts,null);
        thirdUser.setId(Context.now()+2000);
        UsersDao.instance.insertOne(thirdUser);
        firstUser = UsersDao.instance.getFirstUser(ACCOUNT_ID);
        assertEquals(firstUser.getLogin(), thirdUser.getLogin());

        User fourthUser = new User("ankita", "ankita@bigcorp.com", accounts,null);
        fourthUser.setId(Context.now()+3000);
        UsersDao.instance.insertOne(fourthUser);
        firstUser = UsersDao.instance.getFirstUser(ACCOUNT_ID);
        assertEquals(firstUser.getLogin(), thirdUser.getLogin());
    }
}
