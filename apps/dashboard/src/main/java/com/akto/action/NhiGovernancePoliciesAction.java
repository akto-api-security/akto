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

import java.util.ArrayList;
import java.util.List;

public class NhiGovernancePoliciesAction extends UserAction {

    private static final LoggerMaker loggerMaker = new LoggerMaker(NhiGovernancePoliciesAction.class, LogDb.DASHBOARD);

    @Getter private List<NhiPolicy> policies;
    @Getter @Setter private NhiPolicy policy;
    @Getter private boolean success = false;

    @Setter private String policyId;

    public String fetchNhiPolicies() {
        try {
            policies = NhiPolicyDao.instance.findAll(Filters.empty());
            return Action.SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error fetching NHI policies: " + e.getMessage());
            addActionError(e.getMessage());
            return Action.ERROR.toUpperCase();
        }
    }

    public String saveNhiPolicy() {
        try {
            if (policy == null) {
                addActionError("Policy is required");
                return Action.ERROR.toUpperCase();
            }

            boolean isNew = (policyId == null || policyId.isEmpty());
            if (isNew && (policy.getPolicyName() == null || policy.getPolicyName().trim().isEmpty())) {
                addActionError("Policy name is required");
                return Action.ERROR.toUpperCase();
            }

            ObjectId id = isNew ? new ObjectId() : new ObjectId(policyId);
            int now = Context.now();
            String user = getSUser().getLogin();

            List<Bson> updates = new ArrayList<>();
            if (policy.getPolicyName() != null) {
                updates.add(Updates.set(NhiPolicy.POLICY_NAME, policy.getPolicyName()));
            }
            if (policy.getDescription() != null) {
                updates.add(Updates.set(NhiPolicy.DESCRIPTION, policy.getDescription()));
            }
            if (policy.getStatus() != null && !policy.getStatus().isEmpty()) {
                updates.add(Updates.set(NhiPolicy.STATUS, policy.getStatus()));
            } else {
                updates.add(Updates.setOnInsert(NhiPolicy.STATUS, "ACTIVE"));
            }
            if (policy.getScope() != null) {
                updates.add(Updates.set(NhiPolicy.SCOPE, policy.getScope()));
            }
            if (policy.getTokenSegregation() != null) {
                updates.add(Updates.set(NhiPolicy.TOKEN_SEGREGATION, policy.getTokenSegregation()));
            }
            if (policy.getExpirationTracking() != null) {
                updates.add(Updates.set(NhiPolicy.EXPIRATION_TRACKING, policy.getExpirationTracking()));
            }
            if (policy.getRotationEnforcement() != null) {
                updates.add(Updates.set(NhiPolicy.ROTATION_ENFORCEMENT, policy.getRotationEnforcement()));
            }

            updates.add(Updates.set(NhiPolicy.UPDATED_AT, now));
            updates.add(Updates.set(NhiPolicy.UPDATED_BY, user));
            updates.add(Updates.setOnInsert(NhiPolicy.CREATED_AT, now));
            updates.add(Updates.setOnInsert(NhiPolicy.CREATED_BY, user));

            Bson filter = Filters.eq(NhiPolicy.ID, id);
            NhiPolicyDao.instance.updateOne(filter, Updates.combine(updates));

            success = true;
            return Action.SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error saving NHI policy: " + e.getMessage());
            addActionError(e.getMessage());
            return Action.ERROR.toUpperCase();
        }
    }
}
