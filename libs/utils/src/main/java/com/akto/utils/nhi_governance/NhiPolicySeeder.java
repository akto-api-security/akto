package com.akto.utils.nhi_governance;

import com.akto.dao.context.Context;
import com.akto.dao.nhi_governance.NhiPolicyDao;
import com.akto.dto.nhi_governance.NhiPolicy;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.mongodb.client.model.Filters;

import java.util.ArrayList;
import java.util.List;

public final class NhiPolicySeeder {

    private static final LoggerMaker logger =
            new LoggerMaker(NhiPolicySeeder.class, LogDb.DASHBOARD);

    private NhiPolicySeeder() {}

    public static void seedIfEmpty() {
        long existing;
        try {
            existing = NhiPolicyDao.instance.count(Filters.empty());
        } catch (Exception e) {
            logger.errorAndAddToDb(e,
                    "NHI seeder: count failed for account=" + Context.accountId.get()
                            + ": " + e.getMessage());
            return;
        }
        if (existing > 0) return;

        int now = (int)(System.currentTimeMillis() / 1000);
        int inserted = 0;
        for (NhiPolicy p : buildDefaults(now)) {
            try {
                NhiPolicyDao.instance.insertOne(p);
                inserted++;
            } catch (Exception e) {
                logger.errorAndAddToDb(e,
                        "NHI seeder: insert failed for '" + p.getPolicyName()
                                + "': " + e.getMessage());
            }
        }
        logger.infoAndAddToDb("NHI seeder: inserted " + inserted + " default policies");
    }

    private static List<NhiPolicy> buildDefaults(int now) {
        List<NhiPolicy> out = new ArrayList<>();

        NhiPolicy exp = new NhiPolicy(
                "Credential expiration tracking",
                "Flag credentials that have expired or are nearing expiration so they can be rotated before they break or get compromised.",
                "ACTIVE"
        );
        NhiPolicy.ExpirationTracking et = new NhiPolicy.ExpirationTracking();
        et.setEnabled(true);
        et.setWarningThresholdMonths(1);
        et.setFlagExpiredTokens(true);
        exp.setExpirationTracking(et);
        stamp(exp, now);
        out.add(exp);

        NhiPolicy seg = new NhiPolicy(
                "No token reuse across agents",
                "Each AI agent should have its own scoped credentials. Reusing a token across multiple agents broadens blast radius if any one of them is compromised.",
                "ACTIVE"
        );
        seg.setTokenSegregation(new NhiPolicy.TokenSegregation(true));
        stamp(seg, now);
        out.add(seg);

        NhiPolicy rotation = new NhiPolicy(
                "API credential rotation enforcement",
                "All API keys and tokens must be rotated within 30 days. Keys older than 30 days without rotation trigger a High-severity violation; keys within 7 days of the deadline trigger a Medium-severity reminder.",
                "ACTIVE"
        );
        NhiPolicy.RotationEnforcement re = new NhiPolicy.RotationEnforcement();
        re.setEnabled(true);
        re.setMaxAgeDays(30);
        re.setWarningDays(7);
        rotation.setRotationEnforcement(re);
        stamp(rotation, now);
        out.add(rotation);

        return out;
    }

    private static void stamp(NhiPolicy p, int now) {
        p.setCreatedAt(now);
        p.setCreatedBy("system");
        p.setUpdatedAt(now);
        p.setUpdatedBy("system");
    }
}
