package com.akto.dao.jobs;

import com.akto.dao.CommonContextDao;
import com.akto.dto.jobs.Job;

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
}
