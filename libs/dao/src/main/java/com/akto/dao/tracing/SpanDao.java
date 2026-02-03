package com.akto.dao.tracing;

import com.akto.dao.AccountsContextDao;
import com.akto.dto.tracing.Span;

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
    }
}
