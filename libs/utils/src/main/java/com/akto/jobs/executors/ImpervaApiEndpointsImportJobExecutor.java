package com.akto.jobs.executors;

import com.akto.dao.ImpervaIntegrationDao;
import com.akto.dao.jobs.JobsDao;
import com.akto.dto.imperva_integration.ImpervaIntegration;
import com.akto.dto.jobs.ImpervaApiEndpointsImportJobParams;
import com.akto.dto.jobs.Job;
import com.akto.dto.jobs.JobStatus;
import com.akto.jobs.JobExecutor;
import com.akto.log.LoggerMaker;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;

import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;

public class ImpervaApiEndpointsImportJobExecutor extends JobExecutor<ImpervaApiEndpointsImportJobParams> {

    private static final LoggerMaker logger = new LoggerMaker(ImpervaApiEndpointsImportJobExecutor.class, LoggerMaker.LogDb.DASHBOARD);
    public static final ImpervaApiEndpointsImportJobExecutor INSTANCE = new ImpervaApiEndpointsImportJobExecutor();

    // set by the jobs service; takes the integration and a callback that sends a heartbeat and returns false once the job is stopped
    private BiConsumer<ImpervaIntegration, BooleanSupplier> impervaApiEndpointsImporter;

    public void setImpervaApiEndpointsImporter(BiConsumer<ImpervaIntegration, BooleanSupplier> impervaApiEndpointsImporter) {
        this.impervaApiEndpointsImporter = impervaApiEndpointsImporter;
    }

    public ImpervaApiEndpointsImportJobExecutor() {
        super(ImpervaApiEndpointsImportJobParams.class);
    }

    @Override
    protected void runJob(Job job) throws Exception {
        if (impervaApiEndpointsImporter == null) {
            logger.errorAndAddToDb("impervaApiEndpointsImporter is not configured. Skipping Imperva API endpoints import.");
            return;
        }

        ImpervaIntegration integration = ImpervaIntegrationDao.instance.findOne(new BasicDBObject());
        // only the latest import job runs, so a duplicate trigger can't start a second import
        if (integration == null || !job.getId().equals(integration.getImportJobId())) {
            logger.infoAndAddToDb("Imperva import job " + job.getId() + " is not the latest one. Skipping.");
            return;
        }

        impervaApiEndpointsImporter.accept(integration, () -> {
            updateJobHeartbeat(job);
            Job latest = JobsDao.instance.findOne(Filters.eq(Job.ID, job.getId()));
            return latest != null && latest.getJobStatus() == JobStatus.RUNNING;
        });
    }
}
