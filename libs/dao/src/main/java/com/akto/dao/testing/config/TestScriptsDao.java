package com.akto.dao.testing.config;

import com.akto.dao.*;
import com.akto.dto.testing.config.TestScript;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import org.bson.conversions.Bson;

public class TestScriptsDao extends AccountsContextDao<TestScript> {

    public static final TestScriptsDao instance = new TestScriptsDao();

    private TestScriptsDao() {}

    public TestScript fetchTestScript(TestScript.Type type) {
        if (type == null) {
            return TestScriptsDao.instance.findOne(new BasicDBObject());
        }
        Bson filter = Filters.eq(TestScript.TYPE, type.name());
        return TestScriptsDao.instance.findOne(filter);
    }

    @Override
    public String getCollName() {
        return "test_collection_properties";
    }

    @Override
    public Class<TestScript> getClassT() {
        return TestScript.class;
    }
}
