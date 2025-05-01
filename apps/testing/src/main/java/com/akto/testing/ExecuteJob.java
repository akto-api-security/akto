package com.akto.testing;

import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.google.cloud.run.v2.EnvVar;
import com.google.cloud.run.v2.JobName;
import com.google.cloud.run.v2.JobsClient;
import com.google.cloud.run.v2.RunJobRequest;

public class ExecuteJob {

    private static final LoggerMaker loggerMaker = new LoggerMaker(ExecuteJob.class, LogDb.TESTING);

    public static void executeTestingJob(int accountId, String testingRunHexId) {
        String gcpProject = System.getenv("GCP_PROJECT");
        try (JobsClient jobsClient = JobsClient.create()) {
            JobName name = JobName.of(gcpProject, "us-central1", "hello-world-job");

            EnvVar jobAccountId = EnvVar.newBuilder()
                .setName("JOB_ACCOUNT_ID")
                .setValue(String.valueOf(accountId))
                .build();
            
            EnvVar jobTestingRunHexId = EnvVar.newBuilder()
                    .setName("JOB_TESTING_RUN_HEX_ID")
                    .setValue(testingRunHexId)
                    .build();

            RunJobRequest request = RunJobRequest.newBuilder()
                    .setName(name.toString())
                    .setOverrides(RunJobRequest.Overrides.newBuilder()
                            .addContainerOverrides(RunJobRequest.Overrides.ContainerOverride.newBuilder()
                                    .addEnv(jobAccountId)
                                    .addEnv(jobTestingRunHexId))
                            .build())
                    .build();

            jobsClient.runJobAsync(request).get();
            loggerMaker.info("Testing job submitted for execution");
        } catch (Exception e) {
            loggerMaker.error("Error while submitting job");
            e.printStackTrace();
        }
    }
}
