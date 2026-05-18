package com.akto.action;

import com.akto.dao.context.Context;
import com.akto.dao.nhi_governance.NhiPolicyDao;
import com.akto.dto.nhi_governance.NhiPolicy;
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

public class NhiGovernancePoliciesAction extends UserAction {

    private static final LoggerMaker loggerMaker = new LoggerMaker(NhiGovernancePoliciesAction.class, LogDb.DASHBOARD);

    @Getter private List<NhiPolicy> policies;
    @Getter @Setter private NhiPolicy policy;
    @Getter private boolean success = false;

    @Setter private String contextSource;
    @Setter private String policyId;
    @Setter private String userEmail;

    public String fetchNhiPolicies() {
        try {
            Bson filter = (contextSource != null && !contextSource.isEmpty())
                    ? Filters.eq(NhiPolicy.CONTEXT_SOURCE, contextSource)
                    : Filters.empty();
            policies = NhiPolicyDao.instance.findAll(filter);
            return Action.SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error fetching NHI policies: " + e.getMessage());
            addActionError(e.getMessage());
            return Action.ERROR.toUpperCase();
        }
    }

    public String createNhiPolicy() {
        try {
            if (policy == null || policy.getPolicyName() == null || policy.getPolicyName().trim().isEmpty()) {
                return Action.ERROR.toUpperCase();
            }

            int now = Context.now();
            String creator = userEmail != null ? userEmail : "unknown";

            policy.setCreatedAt(now);
            policy.setCreatedBy(creator);
            policy.setUpdatedAt(now);
            policy.setUpdatedBy(creator);

            if (policy.getStatus() == null || policy.getStatus().isEmpty()) {
                policy.setStatus("ACTIVE");
            }

            NhiPolicyDao.instance.insertOne(policy);
            success = true;
            return Action.SUCCESS.toUpperCase();
        } catch (Exception e) {
            addActionError(e.getMessage());
            return Action.ERROR.toUpperCase();
        }
    }

    public String updateNhiPolicy() {
        try {
            if (policyId == null || policyId.isEmpty()) {
                return Action.ERROR.toUpperCase();
            }
            if (policy == null) {
                return Action.ERROR.toUpperCase();
            }

            int now = Context.now();
            String updater = userEmail != null ? userEmail : "unknown";

            Bson filter = Filters.eq(NhiPolicy.ID, new ObjectId(policyId));
            Bson update = Updates.combine(
                Updates.set(NhiPolicy.POLICY_NAME, policy.getPolicyName()),
                Updates.set(NhiPolicy.DESCRIPTION, policy.getDescription()),
                Updates.set(NhiPolicy.STATUS, policy.getStatus()),
                Updates.set(NhiPolicy.SCOPE, policy.getScope()),
                Updates.set(NhiPolicy.TOKEN_SEGREGATION, policy.getTokenSegregation()),
                Updates.set(NhiPolicy.EXPIRATION_TRACKING, policy.getExpirationTracking()),
                Updates.set(NhiPolicy.UPDATED_AT, now),
                Updates.set(NhiPolicy.UPDATED_BY, updater)
            );

            NhiPolicyDao.instance.updateOne(filter, update);
            loggerMaker.infoAndAddToDb("Updated NHI policy: " + policyId);
            success = true;
            return Action.SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error updating NHI policy: " + e.getMessage());
            addActionError(e.getMessage());
            return Action.ERROR.toUpperCase();
        }
    }

    public String disableNhiPolicy() {
        try {
            if (policyId == null || policyId.isEmpty()) {
                return Action.ERROR.toUpperCase();
            }

            int now = Context.now();
            String updater = userEmail != null ? userEmail : "unknown";

            Bson filter = Filters.eq(NhiPolicy.ID, new ObjectId(policyId));
            Bson update = Updates.combine(
                Updates.set(NhiPolicy.STATUS, "INACTIVE"),
                Updates.set(NhiPolicy.UPDATED_AT, now),
                Updates.set(NhiPolicy.UPDATED_BY, updater)
            );

            NhiPolicyDao.instance.updateOne(filter, update);
            success = true;
            return Action.SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error disabling NHI policy: " + e.getMessage());
            addActionError(e.getMessage());
            return Action.ERROR.toUpperCase();
        }
    }
}
