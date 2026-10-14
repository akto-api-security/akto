package com.akto.dao;

import com.akto.dto.AgenticPostureScoreHistory;

public class AgenticPostureScoreHistoryDao extends AccountsContextDao<AgenticPostureScoreHistory> {

    public static final AgenticPostureScoreHistoryDao instance = new AgenticPostureScoreHistoryDao();

    public void createIndicesIfAbsent() {
        String[] fieldNames = new String[] { AgenticPostureScoreHistory.COMPUTED_AT };
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, false);
    }

    @Override
    public String getCollName() {
        return "agentic_posture_score_history";
    }

    @Override
    public Class<AgenticPostureScoreHistory> getClassT() {
        return AgenticPostureScoreHistory.class;
    }
}
