package com.akto.testing;

import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.google.cloud.run.v2.JobName;
import com.google.cloud.run.v2.JobsClient;

public class ExecuteJob {

    private static final LoggerMaker loggerMaker = new LoggerMaker(ExecuteJob.class, LogDb.TESTING);

    public static void executeTestingJob(int accountId, String testingRunHexId) {
        String gcpProject = System.getenv("GCP_PROJECT");
        try (JobsClient jobsClient = JobsClient.create()) {
            JobName name = JobName.of(gcpProject, "asia-south1", "hello-world-job");
            jobsClient.runJobAsync(name).get();
            loggerMaker.info("Testing job submitted for execution");
        } catch (Exception e) {
            loggerMaker.error("Error while submitting job");
            e.printStackTrace();
        }
    }
}
