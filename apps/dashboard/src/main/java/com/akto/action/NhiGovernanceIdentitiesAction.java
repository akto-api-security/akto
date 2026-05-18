package com.akto.action;

import com.akto.dao.context.Context;
import com.akto.dao.nhi_governance.NhiIdentityDao;
import com.akto.dto.nhi_governance.NhiIdentity;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.opensymphony.xwork2.Action;
import lombok.Getter;
import lombok.Setter;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import java.util.List;

public class NhiGovernanceIdentitiesAction extends UserAction {

    private static final LoggerMaker loggerMaker = new LoggerMaker(NhiGovernanceIdentitiesAction.class, LogDb.DASHBOARD);

    @Getter
    private List<NhiIdentity> identities;

    @Getter
    @Setter
    private NhiIdentity identity;

    @Getter
    private boolean success = false;

    @Setter
    private String contextSource;

    @Setter
    private String identityId;

    @Setter
    private String userEmail;

    public String fetchNhiIdentities() {
        try {
            int accountId = Context.accountId.get();
            loggerMaker.infoAndAddToDb("Fetching NHI identities for account: " + accountId);

            // Build filter based on contextSource if provided
            Bson filter;
            if (contextSource != null && !contextSource.isEmpty()) {
                filter = Filters.eq(NhiIdentity.CONTEXT_SOURCE, contextSource);
                loggerMaker.infoAndAddToDb("Applied filter for contextSource: " + contextSource);
            } else {
                filter = Filters.empty();
            }

            // Fetch all identities
            identities = NhiIdentityDao.instance.findAll(filter);
            loggerMaker.infoAndAddToDb("Found " + identities.size() + " identities");

            return Action.SUCCESS.toUpperCase();

        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error fetching NHI identities: " + e.getMessage());
            addActionError(e.getMessage());
            return Action.ERROR.toUpperCase();
        }
    }

    public String fetchNhiIdentityById() {
        try {
            int accountId = Context.accountId.get();

            if (identity == null || identity.getId() == null) {
                loggerMaker.errorAndAddToDb("Identity ID not provided");
                addActionError("Identity ID is required");
                return Action.ERROR.toUpperCase();
            }

            loggerMaker.infoAndAddToDb("Fetching NHI identity by ID: " + identity.getId() + " for account: " + accountId);

            // Fetch identity by ID
            identity = NhiIdentityDao.instance.findOne(NhiIdentity.ID, identity.getId());

            if (identity == null) {
                loggerMaker.errorAndAddToDb("Identity not found");
                addActionError("Identity not found");
                return Action.ERROR.toUpperCase();
            }

            return Action.SUCCESS.toUpperCase();

        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error fetching NHI identity: " + e.getMessage());
            addActionError(e.getMessage());
            return Action.ERROR.toUpperCase();
        }
    }

    public String disableNhiIdentity() {
        try {
            int accountId = Context.accountId.get();
            long currentTime = System.currentTimeMillis() / 1000; // Unix timestamp in seconds

            if (identityId == null || identityId.isEmpty()) {
                loggerMaker.errorAndAddToDb("Identity ID not provided");
                addActionError("Identity ID is required");
                success = false;
                return Action.ERROR.toUpperCase();
            }

            if (userEmail == null || userEmail.isEmpty()) {
                loggerMaker.errorAndAddToDb("User email not provided");
                addActionError("User email is required");
                success = false;
                return Action.ERROR.toUpperCase();
            }

            loggerMaker.infoAndAddToDb("Disabling NHI identity: " + identityId + " by user: " + userEmail);

            // Update identity status to INACTIVE with updatedAt and updatedBy
            Bson filter = Filters.eq(NhiIdentity.ID, new ObjectId(identityId));
            Bson update = Updates.combine(
                Updates.set(NhiIdentity.STATUS, "INACTIVE"),
                Updates.set(NhiIdentity.UPDATED_AT, (int)currentTime),
                Updates.set(NhiIdentity.UPDATED_BY, userEmail)
            );

            NhiIdentityDao.instance.updateOne(filter, update);
            loggerMaker.infoAndAddToDb("Successfully disabled identity: " + identityId);

            success = true;
            return Action.SUCCESS.toUpperCase();

        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error disabling NHI identity: " + e.getMessage());
            addActionError(e.getMessage());
            success = false;
            return Action.ERROR.toUpperCase();
        }
    }
}
