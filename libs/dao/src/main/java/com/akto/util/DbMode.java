package com.akto.util;

import com.mongodb.ConnectionString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class DbMode {
    private static final Logger logger = LoggerFactory.getLogger(DbMode.class);
    public enum DbType {
        MONGO_DB, COSMOS_DB, DOCUMENT_DB;
    }

    public enum SetupType {
        STANDALONE, CLUSTER
    }

    public static DbType dbType = DbType.MONGO_DB;
    public static SetupType setupType = SetupType.STANDALONE;

    public static void refreshSetupType(ConnectionString connectionString) {
        List<String> hosts = connectionString.getHosts();
        setupType = hosts.size() > 1 ? SetupType.CLUSTER : SetupType.STANDALONE;
    }

    public static void refreshDbType(String connectionString)  {
        String dbTypeEnv = System.getenv("DB_TYPE");
        logger.info("dbTypeEnv: {}", dbTypeEnv);
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
