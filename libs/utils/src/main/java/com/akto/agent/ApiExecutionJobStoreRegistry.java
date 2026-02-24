package com.akto.agent;

public final class ApiExecutionJobStoreRegistry {
    private static volatile ApiExecutionJobStore instance;

    private ApiExecutionJobStoreRegistry() {}

    public static void set(ApiExecutionJobStore store) {
        instance = store;
    }

    public static ApiExecutionJobStore get() {
        return instance;
    }
}
