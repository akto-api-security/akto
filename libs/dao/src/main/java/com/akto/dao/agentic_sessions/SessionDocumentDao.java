package com.akto.dao.agentic_sessions;

import com.akto.dao.AccountsContextDao;
import com.akto.dto.agentic_sessions.SessionDocument;

public class SessionDocumentDao extends AccountsContextDao<SessionDocument> {

    @Override
    public String getCollName() {
        return "agentic_session_context";
    }

    public static final SessionDocumentDao instance = new SessionDocumentDao();

    private SessionDocumentDao() {
    }

    @Override
    public Class<SessionDocument> getClassT() {
        return SessionDocument.class;
    }
}
