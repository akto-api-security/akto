package com.akto.dao.nhi_governance;

import com.akto.dao.AccountsContextDao;
import com.akto.dao.MCollection;
import com.akto.dao.context.Context;
import com.akto.dto.nhi_governance.NhiIdentity;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import org.apache.commons.lang3.StringUtils;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import java.util.ArrayList;
import java.util.List;

public class NhiIdentityDao extends AccountsContextDao<NhiIdentity> {

    public static NhiIdentityDao instance = new NhiIdentityDao();

    private static final LoggerMaker logger = new LoggerMaker(NhiIdentityDao.class, LogDb.DASHBOARD);

    @Override
    public String getCollName() {
        return NhiIdentity.COLLECTION_NAME;
    }

    @Override
    public Class<NhiIdentity> getClassT() {
        return NhiIdentity.class;
    }

    public void createIndicesIfAbsent() {
        boolean exists = false;
        for (String col : clients[0].getDatabase(Context.accountId.get() + "").listCollectionNames()) {
            if (getCollName().equalsIgnoreCase(col)) {
                exists = true;
                break;
            }
        }

        if (!exists) {
            clients[0].getDatabase(Context.accountId.get() + "").createCollection(getCollName());
        }


        String[] fieldNames = {NhiIdentity.CONTEXT_SOURCE};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, false);


        fieldNames = new String[]{NhiIdentity.STATUS};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, false);

        fieldNames = new String[]{NhiIdentity.IDENTITY_NAME};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, false);

        fieldNames = new String[]{NhiIdentity.RISK_LEVEL};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, false);

        // compound: contextSource + status — most common combined filter
        fieldNames = new String[]{NhiIdentity.CONTEXT_SOURCE, NhiIdentity.STATUS};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, false);
    }

    public void upsertOne(NhiIdentity ni) {
        if (ni == null) return;
        doUpsert(ni);
    }

    public BatchResult upsertMany(List<NhiIdentity> nis) {
        BatchResult result = new BatchResult();
        if (nis == null || nis.isEmpty()) return result;
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
        return result;
    }

    public static class BatchResult {
        public int upserted;
        public int skipped;
    }

    private void doUpsert(NhiIdentity ni) {
        int now = (int) (System.currentTimeMillis() / 1000);
        Bson filter = buildUpsertKey(ni);
        NhiIdentity existing = findOne(filter);
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

    private void insertNew(NhiIdentity ni, int now) {
        applyDefaults(ni, now);
        if (ni.getId() == null) ni.setId(new ObjectId());
        ni.setCreatedAt(now);
        ni.setLastUsedAt(now);
        insertOne(ni);
    }

    private void refreshExisting(NhiIdentity ni, NhiIdentity existing, int now, Bson filter) {
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
        updates.add(Updates.set(NhiIdentity.EXPIRY_DATE, ni.getExpiryDate()));
        if (existing.getOwner() == null && StringUtils.isNotBlank(ni.getDeviceLabel())) {
            NhiIdentity.Owner o = new NhiIdentity.Owner();
            o.setName(ni.getDeviceLabel());
            updates.add(Updates.set(NhiIdentity.OWNER, o));
        }
        if (ni.getMetadata() != null && !ni.getMetadata().isEmpty()) {
            updates.add(Updates.set(NhiIdentity.METADATA, ni.getMetadata()));
        }
        updateOne(filter, Updates.combine(updates));
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
