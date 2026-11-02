package com.akto.dao;

import com.akto.dao.context.Context;
import com.akto.dto.EndpointAgentOrganization;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class EndpointAgentOrganizationDao extends AccountsContextDao<EndpointAgentOrganization> {

    public static final EndpointAgentOrganizationDao instance = new EndpointAgentOrganizationDao();

    private EndpointAgentOrganizationDao() {}

    // The collection is per account and only ever written from a request thread, so there is no
    // boot-time hook with an account context to create the index from. Track which accounts have
    // been checked in this JVM so the existence check runs once per account, not on every
    // heartbeat — same approach as DbAction's dast_logs capped-collection guard.
    private static final Set<Integer> indicesReady = ConcurrentHashMap.newKeySet();

    public void createIndicesIfAbsent() {
        MCollection.createIndexIfAbsent(getDBName(), getCollName(),
                new String[] { EndpointAgentOrganization.AGENT_TYPE }, false);
    }

    private void ensureIndices() {
        Integer accountId = Context.accountId.get();
        if (accountId == null || !indicesReady.add(accountId)) return;
        try {
            createIndicesIfAbsent();
        } catch (Exception e) {
            // let the next write retry rather than leaving the account marked as done
            indicesReady.remove(accountId);
        }
    }

    @Override
    public String getCollName() {
        return "endpoint_agent_organizations";
    }

    @Override
    public Class<EndpointAgentOrganization> getClassT() {
        return EndpointAgentOrganization.class;
    }

    /**
     * Devices heartbeat continuously, so this runs on every check-in: every operator is
     * setOnInsert, meaning a uuid that is already stored matches and nothing is written at all.
     * A row is only ever created, never rewritten.
     *
     * organizationUuid is the _id, so the uniqueness is mongo's own — there is no index to
     * create, and two devices reporting the same org at the same instant can't both insert.
     */
    public void insertIfAbsent(String organizationUuid, String organizationInfo, String agentType) {
        ensureIndices();
        getMCollection().updateOne(
                Filters.eq(EndpointAgentOrganization.ORGANIZATION_UUID, organizationUuid),
                Updates.combine(
                        // an upsert doesn't write the pojo discriminator on its own, and without
                        // it the row won't decode back into EndpointAgentOrganization
                        Updates.setOnInsert("_t", EndpointAgentOrganization.class.getName()),
                        Updates.setOnInsert(EndpointAgentOrganization.ORGANIZATION_INFO, organizationInfo),
                        Updates.setOnInsert(EndpointAgentOrganization.AGENT_TYPE, agentType),
                        Updates.setOnInsert(EndpointAgentOrganization.CREATED_AT, Context.now())),
                new UpdateOptions().upsert(true));
    }
}
