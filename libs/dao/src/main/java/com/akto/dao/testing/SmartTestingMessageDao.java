package com.akto.dao.testing;

import com.akto.dao.AccountsContextDao;
import com.akto.dao.MCollection;
import com.akto.dto.testing.SmartTestingMessage;

public class SmartTestingMessageDao extends AccountsContextDao<SmartTestingMessage> {

    public static final SmartTestingMessageDao instance = new SmartTestingMessageDao();

    @Override
    public String getCollName() {
        return "smart_testing_messages";
    }

    @Override
    public Class<SmartTestingMessage> getClassT() {
        return SmartTestingMessage.class;
    }

    public void createIndicesIfAbsent() {
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), new String[]{SmartTestingMessage.RUN_KEY, SmartTestingMessage.TIMESTAMP}, true);
    }
}
