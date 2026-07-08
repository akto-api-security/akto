package com.akto.dao.agent_classifiers;

import java.util.ArrayList;
import java.util.List;

import org.bson.conversions.Bson;

import com.akto.dao.AccountsContextDao;
import com.akto.dto.agent_classifiers.AgentGuardCorpusEntry;
import com.mongodb.BasicDBObject;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
public class AgentGuardCorpusDao extends AccountsContextDao<AgentGuardCorpusEntry> {

    public static final String COLLECTION_NAME = "agent_guard_corpus";
    public static final AgentGuardCorpusDao instance = new AgentGuardCorpusDao();

    // Default page size for findByAgentHost when the caller does not specify a limit.
    public static final int DEFAULT_LOAD_LIMIT = 500;

    private AgentGuardCorpusDao() {}

    @Override
    public String getCollName() {
        return COLLECTION_NAME;
    }

    @Override
    public Class<AgentGuardCorpusEntry> getClassT() {
        return AgentGuardCorpusEntry.class;
    }

    public List<BasicDBObject> findBucketsByAgentHost(String agentHost){

        List<BasicDBObject> results = new ArrayList<>();
        List<Bson> pipeline = new ArrayList<>();
        BasicDBObject groupedId = new BasicDBObject(AgentGuardCorpusEntry.AGENT_HOST, "$" + AgentGuardCorpusEntry.AGENT_HOST)
                .append(AgentGuardCorpusEntry.TASK_INTENT, "$" + AgentGuardCorpusEntry.TASK_INTENT)
                .append(AgentGuardCorpusEntry.RISK_CATEGORY, "$" + AgentGuardCorpusEntry.RISK_CATEGORY);
        pipeline.add(Aggregates.match(
            Filters.eq(AgentGuardCorpusEntry.AGENT_HOST, agentHost)
        ));

        pipeline.add(Aggregates.group(groupedId,Accumulators.addToSet("breakDowns", "$" + AgentGuardCorpusEntry.BREAKDOWN)));

        MongoCursor<BasicDBObject> cursor = instance.getMCollection().aggregate(pipeline, BasicDBObject.class).cursor();
        while(cursor.hasNext()) {
            results.add(cursor.next());
        }

        return results;
    }

}
