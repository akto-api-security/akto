package com.akto.utils;

import com.akto.util.DbMode;
import org.junit.Test;

import static com.akto.util.DbMode.refreshDbType;
import static junit.framework.TestCase.assertEquals;

public class TestDbMode {

    @Test
    public void testRefreshDbType() throws Exception {
        assertEquals(DbMode.DbType.MONGO_DB, DbMode.dbType); // default

        refreshDbType("mongodb://user:pass@akto.mongo.cosmos.azure.com:10255/?ssl=true&replicaSet=globaldb&retrywrites=false&maxIdleTimeMS=120000&appName=@akto@");
        assertEquals(DbMode.DbType.COSMOS_DB, DbMode.dbType); // default

        refreshDbType("mongodb://localhost:27017/admini");
        assertEquals(DbMode.DbType.MONGO_DB, DbMode.dbType); // default

        refreshDbType("mongodb://foo.bar.us-west-2.cascades.docdb.aws.dev");
        assertEquals(DbMode.DbType.DOCUMENT_DB, DbMode.dbType); // default

        refreshDbType(null);
        assertEquals(DbMode.DbType.MONGO_DB, DbMode.dbType); // default
    }
}