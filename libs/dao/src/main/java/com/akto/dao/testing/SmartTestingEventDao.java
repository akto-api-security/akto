package com.akto.dao.testing;

import com.akto.dao.AccountsContextDao;
import com.akto.dao.MCollection;
import com.akto.dto.testing.SmartTestingEvent;

public class SmartTestingEventDao extends AccountsContextDao<SmartTestingEvent> {

    public static final SmartTestingEventDao instance = new SmartTestingEventDao();

    @Override
    public String getCollName() {
        return "smart_testing_events";
    }

    @Override
    public Class<SmartTestingEvent> getClassT() {
        return SmartTestingEvent.class;
    }

    public void createIndicesIfAbsent() {
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), new String[]{SmartTestingEvent.RUN_KEY, SmartTestingEvent.SEQ}, true);
        // the live view's poll: a test run's events (runKey in …) from a time on, in order
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), new String[]{SmartTestingEvent.RUN_KEY, SmartTestingEvent.TIMESTAMP, SmartTestingEvent.SEQ}, true);
    }
}
