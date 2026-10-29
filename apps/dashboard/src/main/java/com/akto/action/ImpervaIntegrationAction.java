package com.akto.action;

import org.apache.commons.lang3.StringUtils;

import com.akto.dao.ImpervaIntegrationDao;
import com.akto.dao.context.Context;
import com.akto.dto.imperva_integration.ImpervaIntegration;
import com.akto.dto.jobs.ImpervaApiEndpointsImportJobParams;
import com.akto.dto.jobs.JobExecutorType;
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

    public String addImpervaIntegration() {
        if (StringUtils.isBlank(apiId) || StringUtils.isBlank(apiKey)) {
            addActionError("Please enter a valid API ID and API key.");
            return Action.ERROR.toUpperCase();
        }

        ImpervaIntegrationDao.instance.updateOne(new BasicDBObject(), Updates.combine(
            Updates.set(ImpervaIntegration.API_ID, apiId.trim()),
            Updates.set(ImpervaIntegration.API_KEY, apiKey.trim())
        ));
        return Action.SUCCESS.toUpperCase();
    }

    // the import itself runs as a job in the jobs service
    public String triggerImpervaImport() {
        // the flag is set only if no import is already requested, so a double click doesn't queue two jobs
        ImpervaIntegration integration = ImpervaIntegrationDao.instance.getMCollection().findOneAndUpdate(
            Filters.ne(ImpervaIntegration.SYNC_REQUESTED, true),
            Updates.set(ImpervaIntegration.SYNC_REQUESTED, true)
        );
        if (integration == null) {
            boolean exists = ImpervaIntegrationDao.instance.findOne(new BasicDBObject()) != null;
            addActionError(exists ? "An Imperva import is already in progress." : "Please add the Imperva API ID and API key first.");
            return Action.ERROR.toUpperCase();
        }

        JobScheduler.scheduleRunOnceJob(Context.accountId.get(), new ImpervaApiEndpointsImportJobParams(), JobExecutorType.DASHBOARD);
        return Action.SUCCESS.toUpperCase();
    }

    public String fetchImpervaIntegration() {
        impervaIntegration = ImpervaIntegrationDao.instance.findOne(
            new BasicDBObject(),
            Projections.exclude(ImpervaIntegration.API_KEY)
        );
        return Action.SUCCESS.toUpperCase();
    }
}
