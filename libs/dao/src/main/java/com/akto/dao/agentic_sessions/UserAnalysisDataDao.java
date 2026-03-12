package com.akto.dao.agentic_sessions;

import com.akto.dao.AccountsContextDao;
import com.akto.dao.MCollection;
import com.akto.dto.agentic_sessions.UserAnalysisData;

public class UserAnalysisDataDao extends AccountsContextDao<UserAnalysisData> {

    public static final UserAnalysisDataDao instance = new UserAnalysisDataDao();

    public void createIndicesIfAbsent() {
        String[] fieldNames = new String[]{
                UserAnalysisData.SERVICE_ID,
                UserAnalysisData.UNIQUE_USER_ID
        };
        MCollection.createUniqueIndex(getDBName(), getCollName(), fieldNames, true);
    }

    @Override
    public String getCollName() {
        return "user_analysis_data";
    }

    @Override
    public Class<UserAnalysisData> getClassT() {
        return UserAnalysisData.class;
    }
}
