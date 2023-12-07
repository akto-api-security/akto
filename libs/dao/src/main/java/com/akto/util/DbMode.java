package com.akto.util;

public class DbMode {


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
                System.out.println(err);
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
