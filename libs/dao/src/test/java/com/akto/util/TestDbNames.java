package com.akto.util;

import com.akto.DaoInit;
import com.akto.dao.ApiInfoDao;
import com.akto.dao.LogsDao;
import com.akto.dao.MCollection;
import com.akto.dao.UsersDao;
import com.akto.dao.billing.OrganizationsDao;
import com.akto.dto.ApiInfo;
import com.akto.dto.Log;
import com.akto.dto.User;
import com.akto.dto.UserAccountEntry;
import com.akto.dto.billing.Organization;
import com.akto.dto.type.URLMethods;
import com.akto.utils.MongoBasedTest;
import com.mongodb.MongoNamespace;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.model.RenameCollectionOptions;
import org.bson.Document;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/*
 * With AKTO_DB_NAME_PREFIX set, every database Akto touches must carry the prefix; unset, nothing
 * changes. What is really in Mongo is checked through a raw client that bypasses the prefixing.
 * Run it both ways: plain, and with AKTO_DB_NAME_PREFIX=st_.
 */
public class TestDbNames extends MongoBasedTest {

    private static MongoClient raw;

    @BeforeClass
    public static void openRawClient() {
        raw = MongoClients.create("mongodb://localhost:27019");
    }

    @AfterClass
    public static void closeRawClient() {
        if (raw != null) raw.close();
    }

    @Test
    public void withoutPrefixNothingIsWrapped() {
        if (DbNames.isEnabled()) return;
        assertSame(raw, DbNames.wrap(raw));
        assertEquals("common", DbNames.physical("common"));
        assertEquals("1000000", DbNames.physical("1000000"));
    }

    @Test
    public void commonDaoWritesToPrefixedCommonDb() {
        UsersDao.instance.getMCollection().drop();
        Map<String, UserAccountEntry> accounts = new HashMap<>();
        accounts.put(ACCOUNT_ID + "", new UserAccountEntry(ACCOUNT_ID));
        UsersDao.instance.insertOne(new User("dbnames", "dbnames@akto.io", accounts, null));

        assertNotNull(physicalDoc(DbNames.PREFIX + "common", UsersDao.instance.getCollName()));
    }

    @Test
    public void billingDaoWritesToPrefixedBillingDb() {
        OrganizationsDao.instance.getMCollection().drop();
        OrganizationsDao.instance.insertOne(new Organization("org-1", "dbnames@akto.io", "dbnames", new HashSet<Integer>(), false));

        assertNotNull(physicalDoc(DbNames.PREFIX + "billing", OrganizationsDao.instance.getCollName()));
    }

    @Test
    public void accountDaoWritesToPrefixedAccountDb() {
        ApiInfoDao.instance.getMCollection().drop();
        ApiInfoDao.instance.insertOne(new ApiInfo(new ApiInfo.ApiInfoKey(1, "/dbnames", URLMethods.Method.GET)));

        assertNotNull(physicalDoc(DbNames.PREFIX + ACCOUNT_ID, ApiInfoDao.instance.getCollName()));
    }

    @Test
    public void directGetDatabaseCallsArePrefixedToo() {
        // Many DAOs call clients[0].getDatabase(accountId) directly to create collections.
        String coll = "dbnames_direct";
        MCollection.clients[0].getDatabase(String.valueOf(ACCOUNT_ID)).getCollection(coll).drop();
        MCollection.clients[0].getDatabase(String.valueOf(ACCOUNT_ID)).createCollection(coll);

        assertTrue(collections(DbNames.PREFIX + ACCOUNT_ID).contains(coll));
    }

    @Test
    public void renameStaysInTheSameDatabase() {
        LogsDao.instance.getMCollection().drop();
        LogsDao.instance.getMCollection().insertOne(new Log("x", "k", 1));
        LogsDao.instance.getMCollection().renameCollection(
                new MongoNamespace(LogsDao.instance.getMCollection().getNamespace().getDatabaseName(), "logs_renamed"),
                new RenameCollectionOptions().dropTarget(true));

        assertNotNull(physicalDoc(DbNames.PREFIX + ACCOUNT_ID, "logs_renamed"));
    }

    @Test
    public void startupOnlyEverTouchesPrefixedDatabases() {
        // The platform only has databases it created itself, all prefixed. Run the same index and
        // collection bootstrap the services run at startup, then look at what exists in Mongo.
        try {
            DaoInit.createIndices();
        } catch (com.mongodb.MongoCommandException e) {
            // Some branches' RuntimeMetricsDao creates its collection twice on an empty database
            // (NamespaceExists). Unrelated to naming; what matters here is where things got created.
            if (e.getErrorCode() != 48) throw e;
        }

        List<String> strays = new ArrayList<>();
        for (String db : raw.listDatabaseNames()) {
            if (db.equals("admin") || db.equals("local") || db.equals("config")) continue;
            if (!db.startsWith(DbNames.PREFIX)) strays.add(db);
        }
        assertTrue("databases created without the prefix: " + strays, strays.isEmpty());
    }

    @Test
    public void listedNamesMapBackAndForeignDatabasesAreIgnored() {
        for (String name : new String[]{"common", "billing", String.valueOf(ACCOUNT_ID), "admin"}) {
            assertEquals(name, DbNames.logical(DbNames.physical(name)));
        }
        if (DbNames.isEnabled()) {
            assertNull("another deployment's database", DbNames.logical("other_" + ACCOUNT_ID));
            assertNull("an unprefixed database", DbNames.logical(String.valueOf(ACCOUNT_ID)));
        }
    }

    @Test
    public void invalidPrefixFailsInsteadOfSilentlyRunningUnprefixed() {
        assertEquals("", DbNames.resolvePrefix(null));
        assertEquals("", DbNames.resolvePrefix("  "));
        assertEquals("st_", DbNames.resolvePrefix(" st_ "));
        for (String bad : new String[]{"st.", "a b", "x/y", "$p"}) {
            try {
                DbNames.resolvePrefix(bad);
                fail("accepted invalid prefix " + bad);
            } catch (IllegalStateException expected) {
            }
        }
    }

    private static Document physicalDoc(String db, String coll) {
        return raw.getDatabase(db).getCollection(coll).find().first();
    }

    private static List<String> collections(String db) {
        List<String> out = new ArrayList<>();
        raw.getDatabase(db).listCollectionNames().forEach(out::add);
        return out;
    }
}
