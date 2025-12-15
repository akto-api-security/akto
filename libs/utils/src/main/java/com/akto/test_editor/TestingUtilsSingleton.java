package com.akto.test_editor;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import com.akto.dto.ApiInfo;
import com.akto.dto.RawApi;
import com.akto.mcp.McpRequestResponseUtils;

/**
 * Singleton utilities used by test editor flows.
 * Maintains per-thread API call executors for parallel execution
 * and caches MCP request detection results.
 */
public class TestingUtilsSingleton {

    private static final TestingUtilsSingleton instance = new TestingUtilsSingleton();

    // Thread-local storage for API call executor service
    private final ThreadLocal<ExecutorService> apiCallExecutorService = new ThreadLocal<>();

    public static void init() {
        instance.apiCallExecutorService.remove();
    }

    public static TestingUtilsSingleton getInstance() {
        return instance;
    }

    /** Set the ExecutorService for API calls in the current thread (parallel mode). */
    public void setApiCallExecutorService(ExecutorService executorService) {
        if (executorService != null) {
            apiCallExecutorService.set(executorService);
        } else {
            apiCallExecutorService.remove();
        }
    }

    /** Get the ExecutorService for API calls in the current thread. */
    public ExecutorService getApiCallExecutorService() {
        return apiCallExecutorService.get();
    }

    /** Check if the current thread has an API call executor service (parallel mode). */
    public boolean isParallelApiExecution() {
        return apiCallExecutorService.get() != null;
    }

    /** Clear API call executor service for current thread. */
    public void clearApiCallExecutorService() {
        apiCallExecutorService.remove();
    }

    public static String escapeJsonString(String input) {
        if (input == null) {
            return "";
        }
        return input.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
