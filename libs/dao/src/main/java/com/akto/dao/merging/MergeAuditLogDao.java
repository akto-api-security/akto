package com.akto.dao.merging;

import com.akto.dao.AccountsContextDao;
import com.akto.dto.merging.MergeAuditLog;
import com.mongodb.client.model.Filters;

import java.util.HashSet;
import java.util.Set;

public class MergeAuditLogDao extends AccountsContextDao<MergeAuditLog> {

    public static final MergeAuditLogDao instance = new MergeAuditLogDao();

    @Override
    public String getCollName() {
        return "merge_audit_logs";
    }

    @Override
    public Class<MergeAuditLog> getClassT() {
        return MergeAuditLog.class;
    }

    /** Single server-side distinct call — no doc-level iteration in Java. */
    public Set<String> getAuditedStaticUrls(int apiCollectionId) {
        Set<String> result = new HashSet<>();
        getMCollection().distinct("matchedStaticUrls", Filters.eq(MergeAuditLog.API_COLLECTION_ID, apiCollectionId), String.class)
                .into(result);
        return result;
    }
}
