package com.akto.dao.agentic_sessions;

import com.akto.dao.AccountsContextDao;
import com.akto.dto.agentic_sessions.UserAnalysisData;

public class UserAnalysisDataDao extends AccountsContextDao<UserAnalysisData> {

    public static final UserAnalysisDataDao instance = new UserAnalysisDataDao();

    @Override
    public String getCollName() {
        return "user_analysis_data";
    }

    @Override
    public Class<UserAnalysisData> getClassT() {
        return UserAnalysisData.class;
    }
}
