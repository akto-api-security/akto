package com.akto.action.jobs;

import com.akto.action.UserAction;
import com.akto.dao.jobs.AccountJobDao;
import com.akto.dto.jobs.AccountJob;
import com.akto.jobs.executors.AIAgentConnectorConfigMap;
import com.mongodb.client.model.Filters;
import lombok.Getter;
import lombok.Setter;
import org.bson.types.ObjectId;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AccountJobListAction extends UserAction {

    @Getter @Setter
    private List<Map<String, Object>> accountJobs;

    @Getter @Setter
    private String jobId;

    public String fetchAccountJobs() {
        List<AccountJob> rawJobs = AccountJobDao.instance.findAll(new org.bson.Document());
        accountJobs = new ArrayList<>();
        for (AccountJob job : rawJobs) {
            Map<String, Object> safe = new HashMap<>();
            safe.put("id", job.getId() != null ? job.getId().toHexString() : null);
            safe.put("jobType", job.getJobType());
            safe.put("subType", job.getSubType());
            safe.put("jobStatus", job.getJobStatus() != null ? job.getJobStatus().name() : null);
            safe.put("scheduleType", job.getScheduleType() != null ? job.getScheduleType().name() : null);
            safe.put("scheduledAt", job.getScheduledAt());
            safe.put("startedAt", job.getStartedAt());
            safe.put("finishedAt", job.getFinishedAt());
            safe.put("error", job.getError());
            safe.put("recurringIntervalSeconds", job.getRecurringIntervalSeconds());
            safe.put("createdAt", job.getCreatedAt());
            safe.put("lastUpdatedAt", job.getLastUpdatedAt());
            safe.put("config", AIAgentConnectorConfigMap.getPublicConfig(job.getSubType(), job.getConfig()));
            accountJobs.add(safe);
        }
        return SUCCESS.toUpperCase();
    }

    public String deleteAccountJob() {
        if (jobId == null || jobId.isEmpty()) {
            addActionError("jobId is required");
            return ERROR.toUpperCase();
        }
        try {
            ObjectId objectId = new ObjectId(jobId);
            AccountJobDao.instance.deleteAll(Filters.eq(AccountJob.ID, objectId));
            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            addActionError("Invalid jobId: " + e.getMessage());
            return ERROR.toUpperCase();
        }
    }

    @Override
    public String execute() {
        return SUCCESS.toUpperCase();
    }
}
