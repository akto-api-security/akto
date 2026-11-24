package com.akto.dao.agentic_sessions;

import com.akto.dao.AccountsContextDao;
import com.akto.dao.MCollection;
import com.akto.dto.agentic_sessions.UserAnalysisData;

public class UserAnalysisDataDao extends AccountsContextDao<UserAnalysisData> {

    public static final UserAnalysisDataDao instance = new UserAnalysisDataDao();

    public void createIndicesIfAbsent() {
        MCollection.createIndexIfAbsent(getDBName(), getCollName(),
            new String[]{UserAnalysisData.LAST_UPDATED_AT}, false);

        MCollection.createIndexIfAbsent(getDBName(), getCollName(),
            new String[]{UserAnalysisData.USER_NAME}, false);
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
