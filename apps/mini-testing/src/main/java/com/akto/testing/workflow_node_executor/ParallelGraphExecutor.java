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
import com.akto.test_editor.TestingUtilsSingleton;
import com.akto.test_editor.execution.Memory;

/**
 * Executes graph nodes in parallel. Also propagates a thread-local API executor
 * to enable parallel API calls inside YamlNodeExecutor.
 */
public class ParallelGraphExecutor extends GraphExecutor {

    private static final LoggerMaker loggerMaker = new LoggerMaker(ParallelGraphExecutor.class, LogDb.TESTING);
    private static final int DEFAULT_THREAD_POOL_SIZE = 10;

    private final ExecutorService executorService;
    private final ExecutorService apiCallExecutorService;

    public ParallelGraphExecutor() {
        this.executorService = Executors.newFixedThreadPool(DEFAULT_THREAD_POOL_SIZE);
        this.apiCallExecutorService = Executors.newFixedThreadPool(DEFAULT_THREAD_POOL_SIZE * 2);
    }

    @Override
    public GraphExecutorResult executeGraph(GraphExecutorRequest graphExecutorRequest, boolean debug,
                                            List<TestingRunResult.TestLog> testLogs, Memory memory) {

        Map<String, Node> allNodes = graphExecutorRequest.getGraph().getNodes();
        if (allNodes == null || allNodes.isEmpty()) {
            return handleEmptyGraph(graphExecutorRequest.getWorkflowTestResult());
        }

        ExecutionContext context = initContext(graphExecutorRequest);
        List<CompletableFuture<Void>> futures = submitAllNodes(allNodes, context, debug, testLogs, memory);
        waitForCompletion(allNodes.size(), futures, context.errors);
        return buildResult(graphExecutorRequest, context);
    }

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

    private ExecutionContext initContext(GraphExecutorRequest graphExecutorRequest) {
        return new ExecutionContext(
                graphExecutorRequest.getWorkflowTestResult(),
                graphExecutorRequest.getValuesMap()
        );
    }

    private List<CompletableFuture<Void>> submitAllNodes(Map<String, Node> allNodes, ExecutionContext context, boolean debug, List<TestingRunResult.TestLog> testLogs, Memory memory) {
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (Node node : allNodes.values()) {
            futures.add(CompletableFuture.runAsync(
                    () -> executeNode(node, context, debug, testLogs, memory),
                    executorService
            ));
        }
        return futures;
    }

    private void executeNode(Node node, ExecutionContext context, boolean debug,
                             List<TestingRunResult.TestLog> testLogs, Memory memory) {
        try {
            TestingUtilsSingleton.getInstance().setApiCallExecutorService(apiCallExecutorService);
            if (context.visitedMap.putIfAbsent(node.getId(), true) != null) {
                recordError("Node " + node.getId() + " is being visited multiple times", context);
                return;
            }

            sleepIfNeeded(node);
            WorkflowTestResult.NodeResult nodeResult = Utils.executeNode(
                    node, context.valuesMap, debug, testLogs, memory
            );

            context.nodeResultMap.put(node.getId(), nodeResult);
            context.executionOrder.add(node.getId());
            if (nodeResult.isVulnerable()) {
                context.overallVulnerable.set(true);
            }
            if (nodeResult.getErrors() != null && !nodeResult.getErrors().isEmpty()) {
                context.errors.addAll(nodeResult.getErrors());
            }
            loggerMaker.debugInfoAddToDb("Completed execution of node " + node.getId(), LogDb.TESTING);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            recordError("Node " + node.getId() + " execution was interrupted: " + ie.getMessage(), context);
        } catch (Exception e) {
            recordAndStoreFailure(node, "Error executing node " + node.getId() + ": " + e.getMessage(), context);
        } finally {
            TestingUtilsSingleton.getInstance().clearApiCallExecutorService();
        }
    }

    private void sleepIfNeeded(Node node) throws InterruptedException {
        int waitInSeconds = node.getWaitInSeconds();
        if (waitInSeconds <= 0) {
            return;
        }
        if (waitInSeconds > 100) {
            waitInSeconds = 100;
        }
        loggerMaker.infoAndAddToDb("Node " + node.getId() + " sleeping for " + waitInSeconds + " seconds");
        Thread.sleep(waitInSeconds * 1000L);
    }

    private void waitForCompletion(int nodeCount, List<CompletableFuture<Void>> futures, List<String> errors) {
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(30, TimeUnit.MINUTES);
            loggerMaker.infoAndAddToDb("All " + nodeCount + " nodes completed parallel execution");
        } catch (Exception e) {
            recordError("Error waiting for parallel execution to complete: " + e.getMessage(), errors);
        }
    }

    private GraphExecutorResult buildResult(GraphExecutorRequest request, ExecutionContext context) {
        request.getWorkflowTestResult().getNodeResultMap().putAll(context.nodeResultMap);
        request.getExecutionOrder().addAll(context.executionOrder);
        boolean isVulnerable = context.overallVulnerable.get();
        return new GraphExecutorResult(request.getWorkflowTestResult(), isVulnerable, context.errors);
    }

    private GraphExecutorResult handleEmptyGraph(WorkflowTestResult workflowTestResult) {
        List<String> errors = Collections.synchronizedList(new ArrayList<>());
        loggerMaker.warnAndAddToDb("No nodes found in graph for parallel execution");
        return new GraphExecutorResult(workflowTestResult, false, errors);
    }

    private void recordError(String message, ExecutionContext context) {
        recordError(message, context.errors);
    }

    private void recordError(String message, List<String> errors) {
        loggerMaker.errorAndAddToDb(message);
        errors.add(message);
    }

    private void recordAndStoreFailure(Node node, String message, ExecutionContext context) {
        recordError(message, context);
        List<String> nodeErrors = new ArrayList<>();
        nodeErrors.add(message);
        WorkflowTestResult.NodeResult failureResult = new WorkflowTestResult.NodeResult("{}", false, nodeErrors);
        context.nodeResultMap.put(node.getId(), failureResult);
    }

    private static class ExecutionContext {
        private final Map<String, Boolean> visitedMap = new ConcurrentHashMap<>();
        private final List<String> errors = Collections.synchronizedList(new ArrayList<>());
        private final Map<String, WorkflowTestResult.NodeResult> nodeResultMap = new ConcurrentHashMap<>();
        private final List<String> executionOrder = Collections.synchronizedList(new ArrayList<>());
        private final AtomicBoolean overallVulnerable = new AtomicBoolean(false);
        private final WorkflowTestResult workflowTestResult;
        private final Map<String, Object> valuesMap;

        ExecutionContext(WorkflowTestResult workflowTestResult, Map<String, Object> valuesMap) {
            this.workflowTestResult = workflowTestResult;
            this.valuesMap = valuesMap;
        }
    }
}
