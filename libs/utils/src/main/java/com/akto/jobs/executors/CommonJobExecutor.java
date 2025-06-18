package com.akto.jobs.executors;

import com.akto.dto.jobs.Job;
import com.akto.dto.jobs.JobType;
import com.akto.dto.jobs.McpSyncToolsJobParams;
import com.akto.jobs.JobExecutor;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class CommonJobExecutor extends JobExecutor<McpSyncToolsJobParams> {
    
    public static final CommonJobExecutor INSTANCE = new CommonJobExecutor();
    private static final LoggerMaker logger = new LoggerMaker(CommonJobExecutor.class, LogDb.RUNTIME);
    private static final Map<JobType, Consumer<Job>> functionRegistry = new HashMap<>();
    
    private CommonJobExecutor() {
        super(McpSyncToolsJobParams.class);
    }
    
    @Override
    protected void runJob(Job job) {
        McpSyncToolsJobParams params = paramClass.cast(job.getJobParams());
        logger.debug("Executing common job: {}", job);
        
        Consumer<Job> function = getFunctionFromJob(job);
        if (function != null) {
            function.accept(job);
        } else {
            throw new RuntimeException("No function found for name: " + params.getJobType());
        }
        logger.debug("Completed common job: {}", job);
    }
    
    private Consumer<Job> getFunctionFromJob(Job job) {
        McpSyncToolsJobParams params = paramClass.cast(job.getJobParams());
        return functionRegistry.get(params.getJobType());
    }
    
    public static void registerFunction(JobType jobType, Consumer<Job> function) {
        functionRegistry.put(jobType, function);
        logger.info("Registered function: {}", jobType);
    }
} 