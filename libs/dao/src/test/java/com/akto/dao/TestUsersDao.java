package com.akto.dao;

import com.akto.dao.context.Context;
import com.akto.dto.User;
import com.akto.utils.MongoBasedTest;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TestUsersDao extends MongoBasedTest {

    @Test
    public void testGetFirstUser() {
        UsersDao.instance.getMCollection().drop();
        UsersDao.instance.insertOne(new User("avneesh", "avneesh@akto.io", null,null, null));

        User firstUser = UsersDao.instance.getFirstUser();
        assertEquals(firstUser.getLogin(), "avneesh@akto.io");

        User secondUser = new User("ankush", "ankush@akto.io", null,null, null);
        secondUser.setId(Context.now()+1000);
        UsersDao.instance.insertOne(secondUser);
        firstUser = UsersDao.instance.getFirstUser();
        assertEquals(firstUser.getLogin(), "avneesh@akto.io");
    }
}
