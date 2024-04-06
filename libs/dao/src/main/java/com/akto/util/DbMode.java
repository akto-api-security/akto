package com.akto.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DbMode {
    private static final Logger logger = LoggerFactory.getLogger(DbMode.class);
    public enum DbType {
        MONGO_DB, COSMOS_DB, DOCUMENT_DB;
    }

    public static DbType dbType = DbType.MONGO_DB;

    public static void refreshDbType(String connectionString)  {
        String dbTypeEnv = System.getenv("DB_TYPE");
        if (dbTypeEnv != null && dbTypeEnv.trim().length() > 0) {
            try {
                dbType = DbType.valueOf(dbTypeEnv);
            } catch (Exception e) {
                String err = "Invalid env var DB_TYPE: " + dbTypeEnv;
                logger.error(err);
            }
            return;
        }

        if (connectionString == null) {
            dbType = DbType.MONGO_DB;
            return ;
        }

        if (connectionString.contains("cosmos") && connectionString.contains("azure")) {
            dbType = DbType.COSMOS_DB;
            return ;
        }

        if (connectionString.contains("docdb") && connectionString.contains("aws")) {
            dbType = DbType.DOCUMENT_DB;
            return;
        }

        dbType = DbType.MONGO_DB;

    }

    public static boolean allowCappedCollections() {
        return dbType.equals(DbType.MONGO_DB);
    }
}
