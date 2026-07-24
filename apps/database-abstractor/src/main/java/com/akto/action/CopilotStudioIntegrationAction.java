package com.akto.action;

import com.akto.dao.CopilotStudioIntegrationDao;
import com.akto.dto.CopilotStudioIntegration;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.opensymphony.xwork2.Action;
import com.opensymphony.xwork2.ActionSupport;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import lombok.Getter;
import lombok.Setter;

/**
 * Struts Action for CopilotStudioIntegration-related operations via Cyborg API.
 * Backs CyborgApiClient.findCopilotStudioIntegrationById / updateCopilotStudioIntegration,
 * called from account-job-executor's CopilotStudioMultiEnvExecutor.
 */
@Getter
@Setter
public class CopilotStudioIntegrationAction extends ActionSupport {

    private static final LoggerMaker loggerMaker = new LoggerMaker(CopilotStudioIntegrationAction.class, LogDb.DB_ABS);

    // Input/Output field — reused for both fetch (output) and update (input), same as the client's typed object.
    private CopilotStudioIntegration copilotStudioIntegration;
    private String integrationId;

    /**
     * Fetch a specific CopilotStudioIntegration by ID.
     * The "id" property is excluded from JSON output in struts.xml (raw ObjectId doesn't
     * serialize cleanly) — hexId carries the id as a string instead.
     */
    public String fetchCopilotStudioIntegration() {
        try {
            ObjectId id = new ObjectId(integrationId);

            this.copilotStudioIntegration = CopilotStudioIntegrationDao.instance.findOne(CopilotStudioIntegration.ID, id);

            if (this.copilotStudioIntegration != null) {
                loggerMaker.debug("Fetched CopilotStudioIntegration: integrationId={}", integrationId);
            } else {
                loggerMaker.debug("CopilotStudioIntegration not found: integrationId={}", integrationId);
            }

        } catch (Exception e) {
            loggerMaker.error("Error in fetchCopilotStudioIntegration", e);
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    /**
     * Update a CopilotStudioIntegration's environments and updatedAt. Takes the typed object
     * (deserialized straight into copilotStudioIntegration.environments/updatedAt) instead of a
     * generic field map, so int/List values land in their real types with no manual coercion.
     */
    public String updateCopilotStudioIntegration() {
        try {
            ObjectId id = new ObjectId(integrationId);
            Bson filter = Filters.eq(CopilotStudioIntegration.ID, id);

            Bson update = Updates.combine(
                Updates.set(CopilotStudioIntegration.ENVIRONMENTS, copilotStudioIntegration.getEnvironments()),
                Updates.set(CopilotStudioIntegration.UPDATED_AT, copilotStudioIntegration.getUpdatedAt())
            );

            CopilotStudioIntegrationDao.instance.getMCollection().updateOne(filter, update);
            loggerMaker.debug("Updated CopilotStudioIntegration: integrationId={}", integrationId);

        } catch (Exception e) {
            loggerMaker.error("Error in updateCopilotStudioIntegration", e);
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }
}
