package com.akto.action;

import org.apache.commons.lang3.StringUtils;

import com.akto.dao.ImpervaIntegrationDao;
import com.akto.dao.context.Context;
import com.akto.dto.imperva_integration.ImpervaIntegration;
import com.mongodb.BasicDBObject;
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
        if (Context.accountId.get() != ImpervaIntegration.ENABLED_ACCOUNT_ID) {
            addActionError("Imperva integration is not enabled for this account.");
            return Action.ERROR.toUpperCase();
        }
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

    // the import itself is run by the jobs service, which picks up this flag
    public String triggerImpervaImport() {
        if (Context.accountId.get() != ImpervaIntegration.ENABLED_ACCOUNT_ID) {
            addActionError("Imperva integration is not enabled for this account.");
            return Action.ERROR.toUpperCase();
        }

        ImpervaIntegration integration = ImpervaIntegrationDao.instance.updateOneNoUpsert(
            new BasicDBObject(),
            Updates.set(ImpervaIntegration.SYNC_REQUESTED, true)
        );
        if (integration == null) {
            addActionError("Please add the Imperva API ID and API key first.");
            return Action.ERROR.toUpperCase();
        }
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
