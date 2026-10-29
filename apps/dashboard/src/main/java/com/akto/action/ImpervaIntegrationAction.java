package com.akto.action;

import org.apache.commons.lang3.StringUtils;

import com.akto.dao.ImpervaIntegrationDao;
import com.akto.dao.context.Context;
import com.akto.dao.jobs.JobsDao;
import com.akto.dto.imperva_integration.ImpervaIntegration;
import com.akto.dto.jobs.ImpervaApiEndpointsImportJobParams;
import com.akto.dto.jobs.Job;
import com.akto.dto.jobs.JobExecutorType;
import com.akto.dto.jobs.JobStatus;
import com.akto.jobs.JobScheduler;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Updates;
import com.opensymphony.xwork2.Action;

import lombok.Getter;
import lombok.Setter;

public class ImpervaIntegrationAction extends UserAction {

    @Setter
    private String apiId;

    @Setter
    private String apiKey;

    @Getter
    private ImpervaIntegration impervaIntegration;

    @Getter
    private Job importJob;

    public String addImpervaIntegration() {
        if (StringUtils.isBlank(apiId) || StringUtils.isBlank(apiKey)) {
            addActionError("Please enter a valid API ID and API key.");
            return Action.ERROR.toUpperCase();
        }

        ImpervaIntegrationDao.instance.updateOne(new BasicDBObject(), Updates.combine(
            Updates.set(ImpervaIntegration.API_ID, apiId.trim()),
            Updates.set(ImpervaIntegration.API_KEY, apiKey.trim())
        ));
        return triggerImpervaImport();
    }

    // the import itself runs as a job in the jobs service
    public String triggerImpervaImport() {
        ImpervaIntegration integration = ImpervaIntegrationDao.instance.findOne(new BasicDBObject());
        if (integration == null) {
            addActionError("Please add the Imperva API ID and API key first.");
            return Action.ERROR.toUpperCase();
        }
        if (isActive(findImportJob(integration))) {
            addActionError("An Imperva import is already in progress.");
            return Action.ERROR.toUpperCase();
        }

        Job job = JobScheduler.scheduleRunOnceJob(Context.accountId.get(), new ImpervaApiEndpointsImportJobParams(), JobExecutorType.DASHBOARD);
        if (job == null) {
            addActionError("Could not start the Imperva import. Please try again.");
            return Action.ERROR.toUpperCase();
        }
        ImpervaIntegrationDao.instance.updateOneNoUpsert(new BasicDBObject(), Updates.set(ImpervaIntegration.IMPORT_JOB_ID, job.getId()));
        return Action.SUCCESS.toUpperCase();
    }

    // the running import checks the job status and stops
    public String stopImpervaImport() {
        ImpervaIntegration integration = ImpervaIntegrationDao.instance.findOne(new BasicDBObject());
        if (isActive(findImportJob(integration))) {
            JobScheduler.deleteJob(integration.getImportJobId());
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String fetchImpervaIntegration() {
        impervaIntegration = ImpervaIntegrationDao.instance.findOne(
            new BasicDBObject(),
            Projections.exclude(ImpervaIntegration.API_KEY)
        );
        importJob = findImportJob(impervaIntegration);
        return Action.SUCCESS.toUpperCase();
    }

    private static Job findImportJob(ImpervaIntegration integration) {
        if (integration == null || integration.getImportJobId() == null) {
            return null;
        }
        return JobsDao.instance.findOne(Filters.eq(Job.ID, integration.getImportJobId()));
    }

    private static boolean isActive(Job job) {
        return job != null && (job.getJobStatus() == JobStatus.SCHEDULED || job.getJobStatus() == JobStatus.RUNNING);
    }
}
