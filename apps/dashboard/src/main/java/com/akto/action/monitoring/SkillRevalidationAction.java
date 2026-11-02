package com.akto.action.monitoring;

import com.akto.action.UserAction;
import com.akto.dao.context.Context;
import com.akto.dao.jobs.JobsDao;
import com.akto.dto.User;
import com.akto.dto.jobs.Job;
import com.akto.dto.jobs.JobExecutorType;
import com.akto.dto.jobs.JobStatus;
import com.akto.dto.jobs.JobType;
import com.akto.dto.jobs.SkillRevalidationJobParams;
import com.akto.jobs.JobScheduler;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;

import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Akto users only: validates the skills stored for Endpoint Shield agents again, as a job in the jobs service
public class SkillRevalidationAction extends UserAction {
    private static final LoggerMaker loggerMaker = new LoggerMaker(SkillRevalidationAction.class, LogDb.DASHBOARD);

    // the account's recent jobs looked through for the latest revalidation
    private static final int RECENT_JOBS_LIMIT = 50;

    @Setter
    private List<String> agentIds;

    // status, timestamps and progress of the latest revalidation; null when there has been none
    @Getter
    private Map<String, Object> skillRevalidationJob;

    public String triggerSkillRevalidation() {
        if (!isAktoUser()) {
            addActionError("Only Akto users can revalidate skills.");
            return ERROR.toUpperCase();
        }
        if (agentIds == null || agentIds.isEmpty()) {
            addActionError("Please select at least one agent.");
            return ERROR.toUpperCase();
        }
        Job latest = findLatestJob();
        if (latest != null && isActive(latest)) {
            addActionError("A skill revalidation is already in progress.");
            return ERROR.toUpperCase();
        }

        Job job = JobScheduler.scheduleRunOnceJob(Context.accountId.get(), new SkillRevalidationJobParams(agentIds), JobExecutorType.DASHBOARD);
        if (job == null) {
            addActionError("Could not start the skill revalidation. Please try again.");
            return ERROR.toUpperCase();
        }
        loggerMaker.infoAndAddToDb("Skill revalidation " + job.getId() + " requested by " + getSUser().getLogin() + " for agents " + agentIds);
        skillRevalidationJob = summary(job);
        return SUCCESS.toUpperCase();
    }

    public String fetchSkillRevalidationJob() {
        if (!isAktoUser()) {
            addActionError("Only Akto users can revalidate skills.");
            return ERROR.toUpperCase();
        }
        Job job = findLatestJob();
        skillRevalidationJob = job == null ? null : summary(job);
        return SUCCESS.toUpperCase();
    }

    private static Map<String, Object> summary(Job job) {
        SkillRevalidationJobParams params = (SkillRevalidationJobParams) job.getJobParams();
        Map<String, Object> summary = new HashMap<>();
        summary.put("id", job.getId().toHexString());
        summary.put("status", job.getJobStatus().name());
        summary.put("scheduledAt", job.getScheduledAt());
        summary.put("finishedAt", job.getFinishedAt());
        summary.put("agentCount", params.getAgentIds() == null ? 0 : params.getAgentIds().size());
        summary.put("skillsTotal", params.getSkillsTotal());
        summary.put("skillsRevalidated", params.getSkillsRevalidated());
        summary.put("skillsFailed", params.getSkillsFailed());
        return summary;
    }

    private boolean isAktoUser() {
        User user = getSUser();
        return user != null && user.getLogin() != null && user.getLogin().toLowerCase().endsWith("@akto.io");
    }

    private static boolean isActive(Job job) {
        return job.getJobStatus() == JobStatus.SCHEDULED || job.getJobStatus() == JobStatus.RUNNING;
    }

    private static Job findLatestJob() {
        List<Job> jobs = JobsDao.instance.findAll(
            Filters.eq("accountId", Context.accountId.get()), 0, RECENT_JOBS_LIMIT, Sorts.descending(Job.SCHEDULED_AT));
        for (Job job : jobs) {
            if (job.getJobParams() != null && job.getJobParams().getJobType() == JobType.SKILL_REVALIDATION) {
                return job;
            }
        }
        return null;
    }
}
