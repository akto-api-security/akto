package com.akto.dao.tracing;

import com.akto.dao.AccountsContextDao;
import com.akto.dto.tracing.model.Span;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;

public class SpanDao extends AccountsContextDao<Span> {

    public static final SpanDao instance = new SpanDao();

    private SpanDao() {}

    @Override
    public String getCollName() {
        return "spans";
    }

    @Override
    public Class<Span> getClassT() {
        return Span.class;
    }

    public void createIndicesIfAbsent() {
        // Index on traceId for fetching all spans of a trace
        String[] traceIdIndex = {"traceId"};
        createIndexIfAbsent(getDBName(), getCollName(), traceIdIndex, false);

        // Index on parentSpanId for building span trees
        String[] parentSpanIdIndex = {"parentSpanId"};
        createIndexIfAbsent(getDBName(), getCollName(), parentSpanIdIndex, false);

        // Index on spanKind for filtering by type
        String[] spanKindIndex = {"spanKind"};
        createIndexIfAbsent(getDBName(), getCollName(), spanKindIndex, false);

        // Compound index on traceId and depth for hierarchical queries
        getMCollection().createIndex(
            Indexes.compoundIndex(
                Indexes.ascending("traceId"),
                Indexes.ascending("depth")
            ),
            new IndexOptions().background(true).name("traceId_depth")
        );
    }
}
