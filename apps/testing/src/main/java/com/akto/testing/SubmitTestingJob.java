package com.akto.testing;

import java.nio.file.attribute.AclEntry.Builder;
import java.util.concurrent.TimeUnit;

import com.google.cloud.batch.v1.AllocationPolicy;
import com.google.cloud.batch.v1.AllocationPolicy.InstancePolicy;
import com.google.cloud.batch.v1.AllocationPolicy.InstancePolicyOrTemplate;
import com.google.cloud.batch.v1.BatchServiceClient;
import com.google.cloud.batch.v1.ComputeResource;
import com.google.cloud.batch.v1.CreateJobRequest;
import com.google.cloud.batch.v1.Environment;
import com.google.cloud.batch.v1.Job;
import com.google.cloud.batch.v1.ListJobsRequest;
import com.google.cloud.batch.v1.LocationName;
import com.google.cloud.batch.v1.LogsPolicy;
import com.google.cloud.batch.v1.LogsPolicy.Destination;
import com.google.cloud.batch.v1.Runnable;
import com.google.cloud.batch.v1.Runnable.Barrier;
import com.google.cloud.batch.v1.Runnable.Container;
import com.google.cloud.batch.v1.Runnable.Script;
import com.google.cloud.batch.v1.TaskGroup;
import com.google.cloud.batch.v1.TaskSpec;
import com.google.protobuf.Duration;

public class SubmitTestingJob {
    public void listJobs() {
        try (BatchServiceClient batchServiceClient = BatchServiceClient.create()) {
            String projectId = System.getenv("TESTING_JOB_PROJECT_ID"); // Updated with prefix
            String location = System.getenv("TESTING_JOB_LOCATION"); // Updated with prefix

            LocationName parent = LocationName.of(projectId, location);

            ListJobsRequest request = ListJobsRequest.newBuilder()
                .setParent(parent.toString())
                .build();

            System.out.println("Existing jobs:");
            for (Job job : batchServiceClient.listJobs(request).iterateAll()) {
                System.out.println(job.getName());
            }
        } catch (Exception e) {
            System.err.println("Failed to fetch jobs: " + e.getMessage());
        }
    }

    public void submitTestingJob() {
        try (BatchServiceClient batchServiceClient = BatchServiceClient.create()) {
            String projectId = System.getenv("TESTING_JOB_PROJECT_ID"); // Updated with prefix
            String location = System.getenv("TESTING_JOB_LOCATION"); // Updated with prefix
            String network = System.getenv("TESTING_JOB_NETWORK"); // Updated with prefix
            String subnetwork = System.getenv("TESTING_JOB_SUBNETWORK"); // Updated with prefix

            LocationName parent = LocationName.of(projectId, location);

            // Define what will be done as part of the job.
            Runnable runnable1 = Runnable.newBuilder()
                    .setScript(
                            Script.newBuilder()
                                    .setText(
                                            "while true; do echo Hello from puppeteer; sleep 1; done")
                                    .build())
                    .setBackground(true)
                    .build();
            
            // Define what will be done as part of the job.
            Runnable runnable2 = Runnable.newBuilder()
                    .setScript(
                            Script.newBuilder()
                                    .setText(
                                            "for i in $(seq 1 10); do echo Hello from testing job; sleep 1; done")
                                    .build())
                    .build();

            String testingRunId = "64f1a3b2e4b0c123456789ab";
            // Define what will be done as part of the job.
            Runnable runnable3 = Runnable.newBuilder()
                    .setScript(
                            Script.newBuilder()
                                    .setText(
                                            "echo Testing Run ID: $TESTING_RUN_ID;")
                                    .build())
                    .setEnvironment(
                            Environment.newBuilder()
                                    .putVariables("TESTING_RUN_ID", testingRunId)
                                    .build())
                    .build();

            // Define what will be done as part of the job.
            // Runnable runnableContainer1 = Runnable.newBuilder()
            //         .setContainer(
            //                 Container.newBuilder()
            //                         .setImageUri("orenakto/testing-job:latest")
            //                         .build())
            //         .build();

            // Define what will be done as part of the job.
            // Runnable runnableContainer2 = Runnable.newBuilder()
            //         .setContainer(
            //                 Container.newBuilder()
            //                         .setImageUri("orenakto/testing-job:latest")
            //                         .build())
            //         .build();
            


            // We can specify what resources are requested by each task.
            // ComputeResource computeResource = ComputeResource.newBuilder()
            //         // In milliseconds per cpu-second. This means the task requires 2 whole CPUs.
            //         .setCpuMilli(2000)
            //         // In MiB.
            //         .setMemoryMib(16)
            //         .build();

            TaskSpec task = TaskSpec.newBuilder()
                    // Jobs can be divided into tasks. In this case, we have only one task.
                    .addRunnables(runnable1)
                    .addRunnables(runnable2)
                    .addRunnables(runnable3)
                    // .setComputeResource(computeResource)
                    .setMaxRetryCount(2)
                    .setMaxRunDuration(Duration.newBuilder().setSeconds(3600).build())
                    .build();

            // Tasks are grouped inside a job using TaskGroups.
            // Currently, it's possible to have only one task group.
            TaskGroup taskGroup = TaskGroup.newBuilder().setTaskSpec(task).build();

            InstancePolicy instancePolicy = InstancePolicy.newBuilder().setMachineType("e2-micro").build();

            // Specifies a VPC network and a subnet for Allocation Policy
            AllocationPolicy.NetworkPolicy networkPolicy = AllocationPolicy.NetworkPolicy.newBuilder()
                    .addNetworkInterfaces(AllocationPolicy.NetworkInterface.newBuilder()
                            .setNetwork(network) // Use updated environment variable
                            .setSubnetwork(subnetwork) // Use updated environment variable
                            .build())
                    .build();

            AllocationPolicy allocationPolicy = AllocationPolicy.newBuilder()
                    .addInstances(InstancePolicyOrTemplate.newBuilder().setPolicy(instancePolicy).build())
                    .setNetwork(networkPolicy)
                    .build();

            Job job = Job.newBuilder()
                    .addTaskGroups(taskGroup)
                    .setAllocationPolicy(allocationPolicy)
                    .putLabels("env", "testing")
                    .putLabels("type", "script")
                    // We use Cloud Logging as it's an out of the box available option.
                    .setLogsPolicy(
                            LogsPolicy.newBuilder().setDestination(Destination.CLOUD_LOGGING).build())
                    .build();

            CreateJobRequest createJobRequest = CreateJobRequest.newBuilder()
                    // The job's parent is the region in which the job will run.
                    .setParent(parent.toString())
                    .setJob(job)
                    .setJobId("testing-job-8")
                    .build();

            Job result = batchServiceClient
                    .createJobCallable()
                    .futureCall(createJobRequest)
                    .get(5, TimeUnit.MINUTES);

        System.out.println("Job submitted successfully: " + result.getName());

        } catch (Exception e) {
            System.err.println("Failed to submit job: " + e.getMessage());
        }
    }
}
