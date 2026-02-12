package com.akto.dao;

import com.akto.dto.agentic_sessions.SessionDocument;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;

public class AgenticSessionContextDao extends AccountsContextDao<SessionDocument> {

    public static final AgenticSessionContextDao instance = new AgenticSessionContextDao();

    private AgenticSessionContextDao() {}

    @Override
    public String getCollName() {
        return "agentic_session_context";
    }

    @Override
    public Class<SessionDocument> getClassT() {
        return SessionDocument.class;
    }

    public MongoCollection<SessionDocument> getCollection(String accountId) {
        return clients[0].getDatabase(accountId).getCollection(getCollName(), getClassT());
    }

    public SessionDocument findBySessionIdentifier(String accountId, String sessionIdentifier) {
        return getCollection(accountId)
            .find(Filters.eq(SessionDocument.SESSION_IDENTIFIER, sessionIdentifier))
            .first();
    }
}
