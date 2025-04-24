package com.akto.action;

import com.akto.data_actor.DbLayer;
import com.akto.dto.jobs.AutoTicketParams;
import com.akto.dto.jobs.Job;
import com.akto.dto.jobs.JobParams;
import com.akto.dto.jobs.JobType;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opensymphony.xwork2.Action;
import com.opensymphony.xwork2.ActionSupport;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

public class JobAction extends ActionSupport {

    private static final LoggerMaker loggerMaker = new LoggerMaker(JobAction.class, LogDb.DB_ABS);

    private static final ObjectMapper mapper = new ObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Getter
    @Setter
    Map<String, Object> job;

    public String insertJob() {
        try {
            Job convertedJob = convertMapToJob(job);;
            DbLayer.insertJob(convertedJob);
        } catch (Exception e) {
            loggerMaker.error( "Error in insertJob ", e);
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public Job convertMapToJob(Map<String, Object> jobMap) {
        Map<String, Object> jobParamsMap = (Map<String, Object>) jobMap.remove(Job.JOB_PARAMS);

        Job job = mapper.convertValue(jobMap, Job.class);

        String jobTypeStr = (String) jobParamsMap.get(Job.JOB_PARAMS_JOB_TYPE);
        JobType jobType = JobType.valueOf(jobTypeStr);

        JobParams jobParams;
        switch (jobType) {
            case JIRA_AUTO_CREATE_TICKETS:
                jobParams = mapper.convertValue(jobParamsMap, AutoTicketParams.class);
                break;
            default:
                throw new IllegalArgumentException("Unhandled JobType: " + jobType);
        }
        job.setJobParams(jobParams);
        return job;
    }
}
