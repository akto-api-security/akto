package com.akto.dao.agentic_sessions;

import com.akto.dao.AccountsContextDao;
import com.akto.dao.MCollection;
import com.akto.dto.agentic_sessions.AgentQueryData;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import org.bson.conversions.Bson;

import java.util.concurrent.TimeUnit;

public class AgentQueryDataDao extends AccountsContextDao<AgentQueryData> {

    public static final AgentQueryDataDao instance = new AgentQueryDataDao();

    private static final long TTL_DAYS = 14;

    public void createIndicesIfAbsent() {
        // TTL index: auto-delete records older than 14 days
        Bson ttlIndex = Indexes.ascending(AgentQueryData.TIME_STAMP);
        IndexOptions ttlOptions = new IndexOptions()
                .name("timeStamp_ttl")
                .expireAfter(TTL_DAYS * 24 * 60 * 60, TimeUnit.SECONDS);
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), ttlIndex, ttlOptions);

        // Compound index for cron queries: fetch by user within a time window
        String[] compoundFields = new String[]{
                AgentQueryData.SERVICE_ID,
                AgentQueryData.UNIQUE_USER_ID,
                AgentQueryData.TIME_STAMP
        };
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), compoundFields, false);
    }

    @Override
    public String getCollName() {
        return "agent_query_data";
    }

    @Override
    public Class<AgentQueryData> getClassT() {
        return AgentQueryData.class;
    }
}
