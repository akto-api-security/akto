package com.akto.dao;

import com.akto.dto.agentic_sessions.SessionDocument;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

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

    /**
     * Bulk existence check for a page of sessionIds - one query instead of one-per-event, and
     * projected to just the identifier to keep the payload/index scan cheap.
     */
    public Set<String> findExistingSessionIdentifiers(String accountId, Collection<String> sessionIdentifiers) {
        if (sessionIdentifiers == null || sessionIdentifiers.isEmpty()) {
            return Collections.emptySet();
        }

        Set<String> found = new HashSet<>();
        try (MongoCursor<SessionDocument> cursor = getCollection(accountId)
                .find(Filters.in(SessionDocument.SESSION_IDENTIFIER, sessionIdentifiers))
                .projection(Projections.include(SessionDocument.SESSION_IDENTIFIER))
                .cursor()) {
            while (cursor.hasNext()) {
                found.add(cursor.next().getSessionIdentifier());
            }
        }
        return found;
    }
}
