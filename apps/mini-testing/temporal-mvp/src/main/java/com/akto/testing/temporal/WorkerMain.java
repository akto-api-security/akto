package com.akto.testing.temporal;

import io.temporal.client.WorkflowClient;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import io.temporal.worker.WorkerOptions;

/**
 * The mini-testing worker for ONE customer. In production this runs INSIDE the customer VPC
 * and connects OUTBOUND to the self-hosted Temporal frontend (like it already calls cyborg).
 * It polls only its customer's task queue.
 */
public class WorkerMain {
    public static void main(String[] args) {
        String customerId = System.getenv().getOrDefault("CUSTOMER", "acme");
        String taskQueue = "customer-" + customerId;
        int concurrentBatches = Integer.parseInt(System.getenv().getOrDefault("CONCURRENT_BATCHES", "6"));
        String target = System.getenv().getOrDefault("TEMPORAL_TARGET", "127.0.0.1:7233");

        WorkflowServiceStubs service = WorkflowServiceStubs.newServiceStubs(
                WorkflowServiceStubsOptions.newBuilder().setTarget(target).build());
        WorkflowClient client = WorkflowClient.newInstance(service);
        WorkerFactory factory = WorkerFactory.newInstance(client);

        Worker worker = factory.newWorker(taskQueue,
                WorkerOptions.newBuilder().setMaxConcurrentActivityExecutionSize(concurrentBatches).build());
        worker.registerWorkflowImplementationTypes(TestRunWorkflowImpl.class, ApiShardWorkflowImpl.class);
        worker.registerActivitiesImplementations(new ApiTestingActivitiesImpl());

        factory.start();
        System.out.println("[worker] pid=" + ProcessHandle.current().pid()
                + " started customer=" + customerId + " taskQueue=" + taskQueue
                + " concurrentBatches=" + concurrentBatches + " target=" + target);
    }
}
