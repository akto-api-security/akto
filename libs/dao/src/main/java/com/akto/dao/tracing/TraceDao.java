package com.akto.dao.tracing;

import com.akto.dao.AccountsContextDao;
import com.akto.dto.tracing.model.Trace;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;

public class TraceDao extends AccountsContextDao<Trace> {

    public static final TraceDao instance = new TraceDao();

    private TraceDao() {}

    @Override
    public String getCollName() {
        return "traces";
    }

    @Override
    public Class<Trace> getClassT() {
        return Trace.class;
    }

    public void createIndicesIfAbsent() {
        // Index on rootSpanId for quick lookup
        String[] rootSpanIdIndex = {"rootSpanId"};
        createIndexIfAbsent(getDBName(), getCollName(), rootSpanIdIndex, false);

        // Index on aiAgentName for filtering
        String[] aiAgentNameIndex = {"aiAgentName"};
        createIndexIfAbsent(getDBName(), getCollName(), aiAgentNameIndex, false);

        // Index on startTimeMillis for time-based queries
        String[] startTimeIndex = {"startTimeMillis"};
        createIndexIfAbsent(getDBName(), getCollName(), startTimeIndex, false);

        // Compound index on status and startTime for dashboard queries
        getMCollection().createIndex(
            Indexes.compoundIndex(
                Indexes.ascending("status"),
                Indexes.descending("startTimeMillis")
            ),
            new IndexOptions().background(true).name("status_startTime")
        );
    }
}
