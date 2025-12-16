package com.akto.account_job_executor.executor;

import com.akto.account_job_executor.executor.executors.AIAgentConnectorExecutor;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Factory for AccountJob executors.
 * Uses a registry pattern to map job types to their corresponding executor implementations.
 *
 * To add a new job type:
 * 1. Create a new executor class extending AccountJobExecutor
 * 2. Add it to the registry in the static initializer block
 * 3. Make sure your executor is a singleton (using a public static INSTANCE field)
 */
public class AccountJobExecutorFactory {

    private static final Map<String, AccountJobExecutor> registry;

    static {
        Map<String, AccountJobExecutor> map = new HashMap<>();

        // Register all job executors here
        map.put("AI_AGENT_CONNECTOR", AIAgentConnectorExecutor.INSTANCE);

        // TODO: Add more job types here as needed
        // Example:
        // map.put("SCHEDULED_REPORT", ScheduledReportExecutor.INSTANCE);
        // map.put("DATA_EXPORT", DataExportExecutor.INSTANCE);

        registry = Collections.unmodifiableMap(map);
    }

    /**
     * Get the executor for a given job type.
     *
     * @param jobType The job type string (e.g., "AI_AGENT_CONNECTOR")
     * @return The executor for this job type
     * @throws IllegalArgumentException if no executor is registered for the job type
     */
    public static AccountJobExecutor getExecutor(String jobType) {
        AccountJobExecutor executor = registry.get(jobType);
        if (executor == null) {
            throw new IllegalArgumentException("No executor found for jobType: " + jobType +
                ". Available job types: " + registry.keySet());
        }
        return executor;
    }

    /**
     * Check if an executor is registered for a job type.
     *
     * @param jobType The job type to check
     * @return true if an executor is registered, false otherwise
     */
    public static boolean hasExecutor(String jobType) {
        return registry.containsKey(jobType);
    }

    /**
     * Get all registered job types.
     *
     * @return Set of registered job type strings
     */
    public static java.util.Set<String> getRegisteredJobTypes() {
        return registry.keySet();
    }
}
