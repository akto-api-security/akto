package com.akto.dao;

import com.akto.dto.EndpointShieldDiagnostic;
import com.akto.util.DbMode;
import com.mongodb.client.model.CreateCollectionOptions;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;

public class EndpointShieldDiagnosticDao extends AccountsContextDao<EndpointShieldDiagnostic> {

    public static final String COLLECTION_NAME = "endpoint_shield_diagnostics";

    /**
     * Upper bound on the collection, like logs_endpoint_shield: a capped collection
     * evicts its oldest reports once full. Per device the newest
     * EndpointShieldDiagnostic.RETENTION_PER_DEVICE are kept (trimmed on insert);
     * this caps the account as a whole. Reports are 40–90 KB (at most 512 KB), so
     * 512 MB holds ~7,000 typical reports — 20 each for ~350 devices — before the
     * oldest go. Same size in account changelog 031-endpoint-shield-diagnostics.xml.
     */
    public static final long CAPPED_SIZE_IN_BYTES = 512L * 1024 * 1024;

    public static final EndpointShieldDiagnosticDao instance = new EndpointShieldDiagnosticDao();

    private EndpointShieldDiagnosticDao() {}

    @Override
    public String getCollName() {
        return COLLECTION_NAME;
    }

    @Override
    public Class<EndpointShieldDiagnostic> getClassT() {
        return EndpointShieldDiagnostic.class;
    }

    public void createIndicesIfAbsent() {
        // Capped where the backend supports it (DbMode), as EndpointShieldLogsDao does.
        // convertToCapped drops every non-_id index, so the index is (re)created after.
        if (DbMode.allowCappedCollections()) {
            boolean created = createCollectionIfAbsent(getDBName(), getCollName(),
                    new CreateCollectionOptions().capped(true).sizeInBytes(CAPPED_SIZE_IN_BYTES));
            if (!created && !isCapped()) {
                convertToCappedCollection(CAPPED_SIZE_IN_BYTES);
            }
        } else {
            createCollectionIfAbsent(getDBName(), getCollName(), new CreateCollectionOptions());
        }
        // per-device history, newest first (list + retention trim)
        MCollection.createIndexIfAbsent(getDBName(), getCollName(),
                Indexes.compoundIndex(
                        Indexes.ascending(EndpointShieldDiagnostic.DEVICE_ID),
                        Indexes.descending(EndpointShieldDiagnostic.CREATED_AT)),
                new IndexOptions().name("deviceId_createdAt"));
    }
}
