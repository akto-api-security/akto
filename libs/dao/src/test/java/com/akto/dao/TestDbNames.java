package com.akto.dao;

import com.akto.dao.billing.OrganizationsDao;
import com.akto.dto.User;
import com.akto.dto.UserAccountEntry;
import com.akto.dto.billing.Organization;
import com.akto.util.DbNames;
import com.akto.utils.MongoBasedTest;
import org.junit.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import static org.junit.Assert.assertNotNull;

/*
 * The common/billing DAOs must read and write whichever database DbNames resolved, so that the
 * AKTO_DB_NAME_* env vars actually take effect. Asserts against the resolved value rather than a
 * literal, so it holds both with and without the env vars set.
 */
public class TestDbNames extends MongoBasedTest {

    @Test
    public void commonDaoWritesToResolvedCommonDb() {
        UsersDao.instance.getMCollection().drop();
        Map<String, UserAccountEntry> accounts = new HashMap<>();
        accounts.put(ACCOUNT_ID + "", new UserAccountEntry(ACCOUNT_ID));
        UsersDao.instance.insertOne(new User("dbnames", "dbnames@akto.io", accounts, null));

        assertNotNull("users doc should be in the resolved common DB: " + DbNames.COMMON,
                findFirst(DbNames.COMMON, UsersDao.instance.getCollName()));
    }

    @Test
    public void billingDaoWritesToResolvedBillingDb() {
        OrganizationsDao.instance.getMCollection().drop();
        OrganizationsDao.instance.insertOne(
                new Organization("org-1", "dbnames@akto.io", "dbnames", new HashSet<Integer>(), false));

        assertNotNull("organizations doc should be in the resolved billing DB: " + DbNames.BILLING,
                findFirst(DbNames.BILLING, OrganizationsDao.instance.getCollName()));
    }

    private static org.bson.Document findFirst(String dbName, String collName) {
        return MCollection.clients[0].getDatabase(dbName).getCollection(collName).find().first();
    }
}
