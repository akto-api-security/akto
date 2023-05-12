package com.akto.util.enums;

public class MongoDBEnums {
    public enum DB {
        ACCOUNT
    }

    public enum Collection {

        TESTING_RUN_ISSUES ("testing_run_issues", DB.ACCOUNT),
        ENDPOINT_LOGICAL_GROUP ("endpoint_logical_group", DB.ACCOUNT),
        TEST_ROLES("test_roles", DB.ACCOUNT),
        TESTING_RUN_CONFIG("testing_run_config", DB.ACCOUNT),
        DEMO_REQUEST_FOR_TEMPLATE("demo_request_for_template", DB.ACCOUNT);
        private final DB db;
        private final String collectionName;

        Collection(String name, DB db) {
            this.collectionName = name;
            this.db = db;
        }

        public String getCollectionName() {
            return collectionName;
        }

        public DB getDb() {
            return db;
        }
    }
}
