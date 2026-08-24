package com.akto.action;

import com.akto.dao.CopilotStudioIntegrationDao;
import com.akto.dao.context.Context;
import com.akto.dto.CopilotStudioIntegration;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Updates;
import com.opensymphony.xwork2.Action;
import com.opensymphony.xwork2.ActionSupport;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CopilotStudioIntegrationAction extends ActionSupport {

    private static final LoggerMaker loggerMaker = new LoggerMaker(CopilotStudioIntegrationAction.class, LogDb.DB_ABS);

    // Input/Output field — reused for both fetch (output) and update (input), same as the client's typed object.
    private CopilotStudioIntegration copilotStudioIntegration;
    private String integrationId;
    private String refreshToken;
    // Output only, for fetchAllCopilotStudioIntegrations — every connected tenant for this account.
    private List<CopilotStudioIntegration> copilotStudioIntegrations;

    /** Lists every connected tenant for this account — for CopilotStudioAgentUsersCron (akto/libs/utils), which needs to sync agent_users per tenant, not just look up one integration by id. */
    public String fetchAllCopilotStudioIntegrations() {
        try {
            this.copilotStudioIntegrations = CopilotStudioIntegrationDao.instance.findAll(new BasicDBObject(),
                    Projections.exclude(CopilotStudioIntegration.ENVIRONMENTS));
        } catch (Exception e) {
            loggerMaker.error("Error in fetchAllCopilotStudioIntegrations", e);
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

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
     * Update a CopilotStudioIntegration's environments, updatedAt, refreshToken and status. Takes the
     * typed object (deserialized straight into copilotStudioIntegration's fields) instead of a generic
     * field map, so int/List values land in their real types with no manual coercion.
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

    /** Writes only refreshToken, and never clears one: an empty value is a no-op, not a delete. */
    public String updateCopilotStudioRefreshToken() {
        try {
            if (refreshToken == null || refreshToken.isEmpty()) {
                loggerMaker.warn("Skipping empty refreshToken write: integrationId={}", integrationId);
                return Action.SUCCESS.toUpperCase();
            }

            ObjectId id = new ObjectId(integrationId);
            Bson update = Updates.combine(
                Updates.set(CopilotStudioIntegration.REFRESH_TOKEN, refreshToken),
                Updates.set(CopilotStudioIntegration.UPDATED_AT, Context.now())
            );

            CopilotStudioIntegrationDao.instance.getMCollection()
                .updateOne(Filters.eq(CopilotStudioIntegration.ID, id), update);
            loggerMaker.debug("Updated CopilotStudioIntegration refreshToken: integrationId={}", integrationId);

        } catch (Exception e) {
            loggerMaker.error("Error in updateCopilotStudioRefreshToken", e);
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }
}
