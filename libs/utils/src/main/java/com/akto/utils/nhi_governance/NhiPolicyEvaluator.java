package com.akto.utils.nhi_governance;

import com.akto.dao.nhi_governance.NhiIdentityDao;
import com.akto.dao.nhi_governance.NhiPolicyDao;
import com.akto.dao.nhi_governance.NhiViolationDao;
import com.akto.dto.nhi_governance.NhiIdentity;
import com.akto.dto.nhi_governance.NhiPolicy;
import com.akto.dto.nhi_governance.NhiViolation;
import com.akto.dto.nhi_governance.NhiViolation.IdentityReference;
import com.akto.dto.nhi_governance.NhiViolation.TimelineEntry;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import org.apache.commons.lang3.StringUtils;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class NhiPolicyEvaluator {

    private static final LoggerMaker logger =
            new LoggerMaker(NhiPolicyEvaluator.class, LogDb.DASHBOARD);

    private static final int SECONDS_PER_DAY = 86400;

    private NhiPolicyEvaluator() {}

    public static void evaluate(NhiIdentity identity) {
        if (identity == null || identity.getId() == null) return;

        List<NhiPolicy> policies;
        try {
            policies = NhiPolicyDao.instance.findAll(Filters.eq(NhiPolicy.STATUS, "ACTIVE"));
        } catch (Exception e) {
            logger.errorAndAddToDb("NhiPolicyEvaluator: failed to load policies: " + e.getMessage());
            return;
        }

        int now = (int)(System.currentTimeMillis() / 1000);
        for (NhiPolicy policy : policies) {
            if (!inScope(identity, policy)) continue;
            try {
                if (policy.getExpirationTracking() != null && policy.getExpirationTracking().isEnabled())
                    checkExpiration(identity, policy, now);
                if (policy.getTokenSegregation() != null && policy.getTokenSegregation().isEnabled())
                    checkSegregation(identity, policy, now);
                if (policy.getRotationEnforcement() != null && policy.getRotationEnforcement().isEnabled())
                    checkRotation(identity, policy, now);
            } catch (Exception e) {
                logger.errorAndAddToDb("NhiPolicyEvaluator: policy '" + policy.getPolicyName() + "' failed: " + e.getMessage());
            }
        }
    }

    private static boolean inScope(NhiIdentity identity, NhiPolicy policy) {
        NhiPolicy.Scope scope = policy.getScope();
        if (scope == null) return true;

        List<String> agents = scope.getAgents();
        if (agents != null && !agents.isEmpty()
                && !agents.contains(identity.getAgentName())) {
            return false;
        }
        List<String> nhiIds = scope.getNhiIds();
        if (nhiIds != null && !nhiIds.isEmpty()
                && !nhiIds.contains(identity.getHexId())) {
            return false;
        }
        return true;
    }

    private static void checkExpiration(NhiIdentity ni, NhiPolicy policy, int now) {
        int expiry = ni.getExpiryDate();
        if (expiry <= 0) return;

        NhiPolicy.ExpirationTracking et = policy.getExpirationTracking();
        int warningMonths = et.getWarningThresholdMonths();

        String violationType;
        String severity;
        String description;
        if (expiry <= now) {
            if (!et.isFlagExpiredTokens()) return;
            violationType = "Expired credential still in use";
            severity = "Critical";
            description = "Credential '" + ni.getIdentityName() + "' expired "
                    + ((now - expiry) / SECONDS_PER_DAY)
                    + " days ago but is still configured for " + ni.getAgentName()
                    + ". Rotate immediately and remove the old value from " + ni.getSource() + ".";
        } else if (warningMonths > 0
                && expiry <= now + (long) warningMonths * 30L * SECONDS_PER_DAY) {
            violationType = "Credential expiring soon";
            severity = "High";
            description = "Credential '" + ni.getIdentityName() + "' expires in "
                    + ((expiry - now) / SECONDS_PER_DAY) + " days. Schedule rotation now.";
        } else {
            return;
        }

        upsertViolation(ni, policy, violationType, severity, description, now, Arrays.asList(
                "Rotate the credential and update " + ni.getSource(),
                "Audit recent activity attributed to this credential",
                "Confirm no other agents share this same value"
        ), Collections.singletonList(ni.getAgentName()));
    }

    private static void checkSegregation(NhiIdentity ni, NhiPolicy policy, int now) {
        if (StringUtils.isBlank(ni.getHash())) return;

        List<NhiIdentity> sameHash;
        try {
            sameHash = NhiIdentityDao.instance.findAll(
                    Filters.and(
                            Filters.eq(NhiIdentity.HASH, ni.getHash()),
                            Filters.ne(NhiIdentity.ID, ni.getId())
                    )
            );
        } catch (Exception e) {
            logger.errorAndAddToDb("NhiPolicyEvaluator: segregation lookup failed: " + e.getMessage());
            return;
        }

        Set<String> otherAgents = new HashSet<>();
        List<IdentityReference> related = new ArrayList<>();
        for (NhiIdentity other : sameHash) {
            if (StringUtils.isBlank(other.getAgentName())) continue;
            if (!other.getAgentName().equals(ni.getAgentName())) {
                otherAgents.add(other.getAgentName());
                related.add(new IdentityReference(other.getId(), other.getIdentityName()));
            }
        }
        if (otherAgents.isEmpty()) return;

        related.add(0, new IdentityReference(ni.getId(), ni.getIdentityName()));
        Set<String> allAgents = new HashSet<>(otherAgents);
        if (ni.getAgentName() != null) allAgents.add(ni.getAgentName());

        String description = "The same credential is configured for: "
                + String.join(", ", allAgents)
                + ". Reusing one secret across agents removes blast-radius containment — "
                + "if any single agent is compromised, every other agent that shares this "
                + "credential is also affected.";

        upsertViolation(ni, policy, "Same credential reused across multiple agents",
                "High", description, now, Arrays.asList(
                        "Issue separate credentials per agent (one token, one consumer)",
                        "Revoke the shared credential once each agent has its own",
                        "Apply least privilege — scope each replacement to that agent's needs"
                ), new ArrayList<>(allAgents), related);
    }

    private static void checkRotation(NhiIdentity ni, NhiPolicy policy, int now) {
        NhiPolicy.RotationEnforcement re = policy.getRotationEnforcement();

        // Use lastRotatedAt as baseline; fall back to createdAt for credentials never explicitly rotated.
        int baseline = ni.getLastRotatedAt() > 0 ? ni.getLastRotatedAt()
                     : ni.getCreatedAt() > 0     ? ni.getCreatedAt() : 0;
        if (baseline <= 0) return;

        int maxAgeDays  = re.getMaxAgeDays()  > 0 ? re.getMaxAgeDays()  : 30;
        int warningDays = re.getWarningDays() > 0 ? re.getWarningDays() :  7;
        int ageDays     = (now - baseline) / SECONDS_PER_DAY;

        if (ageDays > maxAgeDays) {
            upsertViolation(ni, policy,
                    "Credential not rotated within policy window",
                    "High",
                    "Credential '" + ni.getIdentityName() + "' was last rotated " + ageDays
                            + " days ago, exceeding the " + maxAgeDays + "-day rotation limit.",
                    now,
                    Arrays.asList(
                            "Rotate the credential and update " + StringUtils.defaultIfBlank(ni.getSource(), "all config files"),
                            "Verify the new credential works before revoking the old one",
                            "Confirm no other agents share the same value after rotation"
                    ),
                    Collections.singletonList(StringUtils.defaultIfBlank(ni.getAgentName(), "Unknown")));
        } else if (ageDays > maxAgeDays - warningDays) {
            int daysLeft = maxAgeDays - ageDays;
            upsertViolation(ni, policy,
                    "Credential approaching rotation deadline",
                    "Medium",
                    "Credential '" + ni.getIdentityName() + "' is due for rotation in " + daysLeft
                            + " day" + (daysLeft == 1 ? "" : "s") + " (policy requires rotation every "
                            + maxAgeDays + " days).",
                    now,
                    Arrays.asList(
                            "Schedule rotation within the next " + daysLeft + " day" + (daysLeft == 1 ? "" : "s"),
                            "Update " + StringUtils.defaultIfBlank(ni.getSource(), "all config files") + " after rotation"
                    ),
                    Collections.singletonList(StringUtils.defaultIfBlank(ni.getAgentName(), "Unknown")));
        }
    }

    private static void upsertViolation(NhiIdentity ni, NhiPolicy policy,
                                        String violationType, String severity,
                                        String description, int now,
                                        List<String> remediation,
                                        List<String> affectedResources) {
        upsertViolation(ni, policy, violationType, severity, description, now,
                remediation, affectedResources,
                Collections.singletonList(new IdentityReference(ni.getId(), ni.getIdentityName())));
    }

    private static void upsertViolation(NhiIdentity ni, NhiPolicy policy,
                                        String violationType, String severity,
                                        String description, int now,
                                        List<String> remediation,
                                        List<String> affectedResources,
                                        List<IdentityReference> identities) {
        String policyId = policy.getHexId();
        Bson filter = Filters.and(
                Filters.eq(NhiViolation.VIOLATION_TYPE, violationType),
                Filters.eq(NhiViolation.IDENTITIES + "._id", ni.getId()),
                Filters.eq(NhiViolation.POLICY_IDS, policyId)
        );

        NhiViolation winner;
        try {
            winner = NhiViolationDao.instance.updateOne(
                    filter,
                    Updates.combine(
                            Updates.setOnInsert(NhiViolation.VIOLATION_TYPE, violationType),
                            Updates.setOnInsert(NhiViolation.STATUS, "Active"),
                            Updates.setOnInsert(NhiViolation.DISCOVERED_AT, now),
                            Updates.setOnInsert(NhiViolation.WHY_TRIGGERED,
                                    "Policy '" + policy.getPolicyName() + "': "
                                            + StringUtils.defaultString(policy.getDescription())),
                            Updates.setOnInsert(NhiViolation.AGENT_NAME, ni.getAgentName()),
                            Updates.setOnInsert(NhiViolation.AGENT_TYPE, ni.getAgentType()),
                            Updates.setOnInsert(NhiViolation.CONTEXT_SOURCE, ni.getContextSource()),
                            Updates.setOnInsert(NhiViolation.POLICY_IDS,
                                    Collections.singletonList(policyId)),
                            Updates.setOnInsert(NhiViolation.TIMELINE, Collections.singletonList(
                                    new TimelineEntry(now, "Violation detected by policy '"
                                            + policy.getPolicyName() + "'"))),
                            Updates.set(NhiViolation.UPDATED_AT, now),
                            Updates.set(NhiViolation.SEVERITY, severity),
                            Updates.set(NhiViolation.DESCRIPTION, description),
                            Updates.set(NhiViolation.AFFECTED_RESOURCES, affectedResources),
                            Updates.set(NhiViolation.REMEDIATION_STEPS, remediation),
                            Updates.set(NhiViolation.IDENTITIES, identities)
                    )
            );
        } catch (Exception e) {
            logger.errorAndAddToDb("NhiPolicyEvaluator: violation upsert failed: " + e.getMessage());
            return;
        }

        // Remove stale duplicates left over from before dedup was enforced.
        if (winner != null && winner.getId() != null) {
            try {
                NhiViolationDao.instance.deleteAll(Filters.and(
                        filter, Filters.ne(NhiViolation.ID, winner.getId())
                ));
            } catch (Exception ignored) {}
        }

        try {
            NhiPolicyDao.instance.updateOne(
                    Filters.eq(NhiPolicy.ID, policy.getId()),
                    Updates.set(NhiPolicy.LAST_TRIGGERED_AT, now)
            );
        } catch (Exception ignored) {
            // best-effort — don't fail the eval pass on a metadata bump
        }
    }
}
