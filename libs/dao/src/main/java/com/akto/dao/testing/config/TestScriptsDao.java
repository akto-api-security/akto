package com.akto.dao.testing.config;

import java.util.List;

import com.akto.dao.AccountsContextDao;
import com.akto.dao.MCollection;
import com.akto.dto.testing.config.TestScript;
import com.mongodb.client.model.CreateCollectionOptions;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Sorts;

/**
 * Repository for the pre/post request script library. Legacy one-script-per-type documents live in a
 * different collection and are read only through TestCollectionPropertiesDao.findLegacyTestScript.
 */
public class TestScriptsDao extends AccountsContextDao<TestScript> {

    public static final TestScriptsDao instance = new TestScriptsDao();

    public static final String COLLECTION_NAME = "test_scripts";
    public static final int MAX_LIST_SIZE = 1000;

    private TestScriptsDao() {}

    public void createIndicesIfAbsent() {
        createCollectionIfAbsent(getDBName(), getCollName(), new CreateCollectionOptions());
        MCollection.createUniqueIndex(getDBName(), getCollName(), new String[]{TestScript.NAME}, true);
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), new String[]{TestScript.TYPE}, true);
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), new String[]{TestScript.LAST_UPDATED_AT}, false);
    }

    public TestScript findById(String id) {
        if (id == null) {
            return null;
        }
        return findOne(Filters.eq(TestScript.ID, id));
    }

    public TestScript findByName(String name) {
        if (name == null) {
            return null;
        }
        return findOne(Filters.eq(TestScript.NAME, name));
    }

    /** Library listing without script bodies, newest first, capped at {@link #MAX_LIST_SIZE}. */
    public List<TestScript> findAllSummaries() {
        return findAll(
            Filters.empty(),
            0,
            MAX_LIST_SIZE,
            Sorts.orderBy(Sorts.descending(TestScript.LAST_UPDATED_AT), Sorts.descending(TestScript.ID)),
            Projections.exclude(TestScript.JAVASCRIPT)
        );
    }

    /**
     * @deprecated The pre-library one-script-per-type read, kept only so existing callers compile until
     * they move to the TestScriptProvider chain. Reads the legacy store exactly as before.
     */
    @Deprecated
    public TestScript fetchTestScript(TestScript.Type type) {
        return type == null ? null : TestCollectionPropertiesDao.instance.findLegacyTestScript(type);
    }

    @Override
    public String getCollName() {
        return COLLECTION_NAME;
    }

    @Override
    public Class<TestScript> getClassT() {
        return TestScript.class;
    }
}
