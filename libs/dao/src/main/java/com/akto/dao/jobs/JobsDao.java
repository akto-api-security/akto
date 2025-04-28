package com.akto.dao.jobs;

import com.akto.dao.CommonContextDao;
import com.akto.dao.MCollection;
import com.akto.dao.context.Context;
import com.akto.dto.jobs.Job;
import com.akto.dto.testing.TestingRun;
import com.mongodb.client.model.CreateCollectionOptions;

public class JobsDao extends CommonContextDao<Job> {

    public static final JobsDao instance = new JobsDao();

    @Override
    public String getCollName() {
        return "jobs";
    }

    @Override
    public Class<Job> getClassT() {
        return Job.class;
    }

    public void createIndicesIfAbsent() {
        createCollectionIfAbsent(getDBName(), getCollName(), new CreateCollectionOptions());

        String[] fieldNames = {Job.JOB_EXECUTOR_TYPE, Job.JOB_STATUS, Job.SCHEDULED_AT};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames,true);

        fieldNames = new String[]{Job.JOB_EXECUTOR_TYPE, Job.JOB_STATUS, Job.HEARTBEAT_AT};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames,true);
    }
}
