package com.akto.dao;

import com.akto.dto.GuardrailResponseRuleRun;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;

import java.util.concurrent.TimeUnit;

public class GuardrailResponseRuleRunsDao extends AccountsContextDao<GuardrailResponseRuleRun> {
    public static final String COLLECTION_NAME = "guardrail_response_rule_runs";
    public static final int RETENTION_DAYS = 30;
    public static final GuardrailResponseRuleRunsDao instance = new GuardrailResponseRuleRunsDao();

    @Override
    public String getCollName() {
        return COLLECTION_NAME;
    }

    @Override
    public Class<GuardrailResponseRuleRun> getClassT() {
        return GuardrailResponseRuleRun.class;
    }

    public void createIndicesIfAbsent() {
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), Indexes.ascending("createdAt"),
            new IndexOptions().name("createdAt_ttl").expireAfter((long) RETENTION_DAYS, TimeUnit.DAYS));
        MCollection.createIndexIfAbsent(getDBName(), getCollName(),
            new String[]{GuardrailResponseRuleRun.STATUS, GuardrailResponseRuleRun.NEXT_ATTEMPT_AT}, true);
    }
}
