package com.akto.testing.workflow_node_executor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;

import com.akto.dto.api_workflow.Node;
import com.akto.dto.testing.GraphExecutorRequest;
import com.akto.dto.testing.GraphExecutorResult;
import com.akto.dto.testing.TestingRunResult;
import com.akto.dto.testing.WorkflowTestResult;
import com.akto.test_editor.execution.Memory;
import com.akto.test_editor.TestingUtilsSingleton;

/**
 * ParallelGraphExecutor executes all nodes in the workflow graph in parallel
 * using a thread pool with a fixed-size queue. This executor is designed for
 * scenarios where nodes can be executed independently without strict ordering requirements.
 */
public class ParallelGraphExecutor extends GraphExecutor {

    private static final LoggerMaker loggerMaker = new LoggerMaker(ParallelGraphExecutor.class, LogDb.TESTING);
    
    // Thread pool size - can be configured based on system resources
    private static final int DEFAULT_THREAD_POOL_SIZE = 10;
    
    private final boolean allowAllCombinations;
    private final ExecutorService executorService;
    private final ExecutorService apiCallExecutorService;
    
    public ParallelGraphExecutor(boolean allowAllCombinations) {
        this.allowAllCombinations = allowAllCombinations;
        // Create a fixed thread pool with bounded queue for node execution
        this.executorService = Executors.newFixedThreadPool(DEFAULT_THREAD_POOL_SIZE);
        // Create a separate thread pool for API calls within nodes
        this.apiCallExecutorService = Executors.newFixedThreadPool(DEFAULT_THREAD_POOL_SIZE * 2);
    }

    @Override
    public GraphExecutorResult executeGraph(GraphExecutorRequest graphExecutorRequest, boolean debug, 
                                           List<TestingRunResult.TestLog> testLogs, Memory memory) {
        
        Map<String, Boolean> visitedMap = new ConcurrentHashMap<>();
        List<String> errors = Collections.synchronizedList(new ArrayList<>());
        WorkflowTestResult workflowTestResult = graphExecutorRequest.getWorkflowTestResult();
        Map<String, Object> valuesMap = graphExecutorRequest.getValuesMap();
        
        // Get all nodes from the graph
        Map<String, Node> allNodes = graphExecutorRequest.getGraph().getNodes();
        
        if (allNodes == null || allNodes.isEmpty()) {
            loggerMaker.warnAndAddToDb("No nodes found in graph for parallel execution");
            return new GraphExecutorResult(workflowTestResult, false, errors);
        }
        
        loggerMaker.infoAndAddToDb("Starting parallel execution of " + allNodes.size() + " nodes");
        
        // Use ConcurrentHashMap for thread-safe operations
        Map<String, WorkflowTestResult.NodeResult> nodeResultMap = new ConcurrentHashMap<>();
        List<String> executionOrder = Collections.synchronizedList(new ArrayList<>());
        AtomicBoolean overallVulnerable = new AtomicBoolean(false);
        
        // Create a list of CompletableFutures for all node executions
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        
        // Submit all nodes for parallel execution
        for (Node node : allNodes.values()) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    // Set API call executor service for this thread - enables queued parallel API calls within nodes
                    TestingUtilsSingleton.getInstance().setApiCallExecutorService(apiCallExecutorService);
                    
                    try {
                        // Check if node was already visited (thread-safe check)
                        if (visitedMap.putIfAbsent(node.getId(), true) != null) {
                            String errorMsg = "Node " + node.getId() + " is being visited multiple times";
                            loggerMaker.warnAndAddToDb(errorMsg);
                            errors.add(errorMsg);
                            return;
                        }
                        
                        // Handle wait time if specified
                        int waitInSeconds = node.getWaitInSeconds();
                        if (waitInSeconds > 0) {
                            if (waitInSeconds > 100) {
                                waitInSeconds = 100;
                            }
                            loggerMaker.infoAndAddToDb("Node " + node.getId() + " sleeping for " + waitInSeconds + " seconds");
                            Thread.sleep(waitInSeconds * 1000);
                        }
                        
                        // Execute the node
                        WorkflowTestResult.NodeResult nodeResult = Utils.executeNode(
                            node, valuesMap, debug, testLogs, memory, this.allowAllCombinations
                        );
                        
                        // Store result (thread-safe)
                        nodeResultMap.put(node.getId(), nodeResult);
                        executionOrder.add(node.getId());
                        
                        // Update overall vulnerable status
                        if (nodeResult.isVulnerable()) {
                            overallVulnerable.set(true);
                        }
                        
                        // Collect errors if any
                        if (nodeResult.getErrors() != null && !nodeResult.getErrors().isEmpty()) {
                            errors.addAll(nodeResult.getErrors());
                        }
                        
                        loggerMaker.debugInfoAddToDb("Completed execution of node " + node.getId(), LogDb.TESTING);
                        
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        String errorMsg = "Node " + node.getId() + " execution was interrupted: " + e.getMessage();
                        loggerMaker.errorAndAddToDb(errorMsg);
                        errors.add(errorMsg);
                    } catch (Exception e) {
                        String errorMsg = "Error executing node " + node.getId() + ": " + e.getMessage();
                        loggerMaker.errorAndAddToDb(errorMsg);
                        errors.add(errorMsg);
                        
                        // Create a failure result for this node
                        List<String> nodeErrors = new ArrayList<>();
                        nodeErrors.add(errorMsg);
                        WorkflowTestResult.NodeResult failureResult = new WorkflowTestResult.NodeResult(
                            "{}", false, nodeErrors
                        );
                        nodeResultMap.put(node.getId(), failureResult);
                    } finally {
                        // Clear API call executor service for this thread
                        TestingUtilsSingleton.getInstance().clearApiCallExecutorService();
                    }
                } catch (Exception e) {
                    // Outer catch for any errors in context setup
                    String errorMsg = "Error setting up execution context for node " + node.getId() + ": " + e.getMessage();
                    loggerMaker.errorAndAddToDb(e, errorMsg);
                    errors.add(errorMsg);
                    TestingUtilsSingleton.getInstance().clearApiCallExecutorService();
                }
            }, executorService);
            
            futures.add(future);
        }
        
        // Wait for all futures to complete
        try {
            CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0])
            );
            
            // Wait with a reasonable timeout (30 minutes)
            allFutures.get(30, TimeUnit.MINUTES);
            
            loggerMaker.infoAndAddToDb("All " + allNodes.size() + " nodes completed parallel execution");
            
        } catch (Exception e) {
            String errorMsg = "Error waiting for parallel execution to complete: " + e.getMessage();
            loggerMaker.errorAndAddToDb(e, errorMsg);
            errors.add(errorMsg);
        }
        
        // Update the workflow test result with all node results
        workflowTestResult.getNodeResultMap().putAll(nodeResultMap);
        graphExecutorRequest.getExecutionOrder().addAll(executionOrder);
        
        // Determine final vulnerable status
        boolean isVulnerable = overallVulnerable.get();
        
        return new GraphExecutorResult(workflowTestResult, isVulnerable, errors);
    }
    
    /**
     * Shutdown the executor services gracefully.
     * This should be called when the executor is no longer needed.
     */
    public void shutdown() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        if (apiCallExecutorService != null && !apiCallExecutorService.isShutdown()) {
            apiCallExecutorService.shutdown();
            try {
                if (!apiCallExecutorService.awaitTermination(60, TimeUnit.SECONDS)) {
                    apiCallExecutorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                apiCallExecutorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}

