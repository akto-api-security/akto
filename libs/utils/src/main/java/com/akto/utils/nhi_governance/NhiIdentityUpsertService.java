package com.akto.utils.nhi_governance;

import com.akto.dao.context.Context;
import com.akto.dao.nhi_governance.NhiIdentityDao;
import com.akto.dto.nhi_governance.NhiIdentity;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.enums.GlobalEnums;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import org.apache.commons.lang3.StringUtils;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import java.util.ArrayList;
import java.util.List;

public final class NhiIdentityUpsertService {

    private static final LoggerMaker logger =
            new LoggerMaker(NhiIdentityUpsertService.class, LogDb.DASHBOARD);

    private NhiIdentityUpsertService() {}

    public static void upsertOne(NhiIdentity ni) {
        if (ni == null) return;
        GlobalEnums.CONTEXT_SOURCE prev = Context.contextSource.get();
        try {
            pinContextSource(ni.getContextSource());
            doUpsert(ni);
        } finally {
            Context.contextSource.set(prev);
        }
    }

    public static BatchResult upsertMany(List<NhiIdentity> nis) {
        BatchResult result = new BatchResult();
        if (nis == null || nis.isEmpty()) return result;

        GlobalEnums.CONTEXT_SOURCE prev = Context.contextSource.get();
        try {
            pinContextSource(nis.get(0).getContextSource());
            for (NhiIdentity ni : nis) {
                try {
                    doUpsert(ni);
                    result.upserted++;
                } catch (Exception e) {
                    result.skipped++;
                    logger.errorAndAddToDb("NHI upsert: skipped identity '"
                            + (ni == null ? "<null>" : ni.getIdentityName())
                            + "': " + e.getMessage());
                }
            }
        } finally {
            Context.contextSource.set(prev);
        }
        return result;
    }

    public static class BatchResult {
        public int upserted;
        public int skipped;
    }

    private static void pinContextSource(String fromIdentity) {
        if (StringUtils.isBlank(fromIdentity)) return;
        try {
            Context.contextSource.set(
                    GlobalEnums.CONTEXT_SOURCE.valueOf(fromIdentity.toUpperCase()));
        } catch (IllegalArgumentException ignored) {
            // Unknown context — leave whatever UserDetailsFilter set.
        }
    }

    private static void doUpsert(NhiIdentity ni) {
        int now = (int)(System.currentTimeMillis() / 1000);
        Bson filter = buildUpsertKey(ni);

        NhiIdentity existing = NhiIdentityDao.instance.findOne(filter);
        if (existing == null) {
            insertNew(ni, now);
        } else {
            refreshExisting(ni, existing, now, filter);
        }
    }

    private static Bson buildUpsertKey(NhiIdentity ni) {
        List<Bson> clauses = new ArrayList<>();
        if (StringUtils.isNotBlank(ni.getHash())) {
            clauses.add(Filters.eq(NhiIdentity.HASH, ni.getHash()));
            if (StringUtils.isNotBlank(ni.getDeviceId())) {
                clauses.add(Filters.eq(NhiIdentity.DEVICE_ID, ni.getDeviceId()));
            }
            if (StringUtils.isNotBlank(ni.getSource())) {
                clauses.add(Filters.eq(NhiIdentity.SOURCE, ni.getSource()));
            }
        } else {
            clauses.add(Filters.eq(NhiIdentity.CONTEXT_SOURCE, ni.getContextSource()));
            clauses.add(Filters.eq(NhiIdentity.IDENTITY_NAME, ni.getIdentityName()));
        }
        return Filters.and(clauses);
    }

    private static void insertNew(NhiIdentity ni, int now) {
        applyDefaults(ni, now);
        if (ni.getId() == null) ni.setId(new ObjectId());
        ni.setCreatedAt(now);
        ni.setLastUsedAt(now);
        NhiIdentityDao.instance.insertOne(ni);
        NhiPolicyEvaluator.evaluate(refetch(ni.getId()));
    }

    private static void refreshExisting(NhiIdentity ni, NhiIdentity existing, int now, Bson filter) {
        List<Bson> updates = new ArrayList<>();
        updates.add(Updates.set(NhiIdentity.UPDATED_AT, now));
        updates.add(Updates.set(NhiIdentity.LAST_USED_AT, now));
        setIfNotBlank(updates, NhiIdentity.IDENTITY_TYPE, ni.getIdentityType());
        setIfNotBlank(updates, NhiIdentity.AGENT_NAME, ni.getAgentName());
        setIfNotBlank(updates, NhiIdentity.AGENT_TYPE, ni.getAgentType());
        setIfNotBlank(updates, NhiIdentity.RISK_LEVEL, ni.getRiskLevel());
        setIfNotBlank(updates, NhiIdentity.SOURCE_TYPE, ni.getSourceType());
        setIfNotBlank(updates, NhiIdentity.DEVICE_LABEL, ni.getDeviceLabel());
        setIfNotBlank(updates, NhiIdentity.PREFIX, ni.getPrefix());
        setIfNotBlank(updates, NhiIdentity.SUFFIX, ni.getSuffix());
        // Mirror scanner's value: positive = real expiry extracted; 0 = opaque token.
        updates.add(Updates.set(NhiIdentity.EXPIRY_DATE, ni.getExpiryDate()));
        // Backfill owner from deviceLabel when the existing record has none
        // (records inserted before applyDefaults set this field).
        if (existing.getOwner() == null && StringUtils.isNotBlank(ni.getDeviceLabel())) {
            NhiIdentity.Owner o = new NhiIdentity.Owner();
            o.setName(ni.getDeviceLabel());
            updates.add(Updates.set(NhiIdentity.OWNER, o));
        }
        if (ni.getMetadata() != null && !ni.getMetadata().isEmpty()) {
            updates.add(Updates.set(NhiIdentity.METADATA, ni.getMetadata()));
        }
        NhiIdentityDao.instance.updateOne(filter, Updates.combine(updates));
        NhiPolicyEvaluator.evaluate(refetch(existing.getId()));
    }

    private static NhiIdentity refetch(ObjectId id) {
        if (id == null) return null;
        try {
            return NhiIdentityDao.instance.findOne(Filters.eq(NhiIdentity.ID, id));
        } catch (Exception e) {
            return null;
        }
    }

    private static void applyDefaults(NhiIdentity ni, int now) {
        if (StringUtils.isBlank(ni.getStatus())) {
            ni.setStatus("ACTIVE");
        }
        if (StringUtils.isBlank(ni.getAccessLevel())) {
            String t = ni.getIdentityType();
            ni.setAccessLevel(("Service Account".equals(t) || "Machine Identity".equals(t))
                    ? "ADMIN" : "READ_WRITE");
        }
        if (ni.getOwner() == null && StringUtils.isNotBlank(ni.getDeviceLabel())) {
            NhiIdentity.Owner o = new NhiIdentity.Owner();
            o.setName(ni.getDeviceLabel());
            ni.setOwner(o);
        }
    }

    private static void setIfNotBlank(List<Bson> updates, String field, String value) {
        if (StringUtils.isNotBlank(value)) {
            updates.add(Updates.set(field, value));
        }
    }
}
