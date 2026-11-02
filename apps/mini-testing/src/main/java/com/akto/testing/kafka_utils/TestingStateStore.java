package com.akto.testing.kafka_utils;

import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.testing.Utils;
import com.akto.util.Constants;
import com.mongodb.BasicDBObject;

public class TestingStateStore {

    public static final String PRODUCER_RUNNING = "PRODUCER_RUNNING";
    public static final String CONSUMER_RUNNING = "CONSUMER_RUNNING";
    public static final String ACCOUNT_ID = "accountId";
    public static final String SUMMARY_ID = "summaryId";
    public static final String TESTING_RUN_ID = "testingRunId";
    public static final String TEST_RUN_MAX_TIME_SECONDS = "testRunMaxTimeSeconds";
    public static final String EXPECTED_RECORDS = "expectedRecords";

    private static final LoggerMaker loggerMaker = new LoggerMaker(TestingStateStore.class, LogDb.TESTING);

    private static volatile BasicDBObject currentState = null;
    private static volatile boolean persistenceFailureLogged = false;

    private TestingStateStore() {}

    /**
     * Stores a snapshot of the state in memory and tries to persist it on disk.
     */
    public static void update(BasicDBObject state) {
        currentState = state == null ? null : new BasicDBObject(state);
        persist(currentState);
    }

    /**
     * Clears the state of the finished run, both in memory and on disk.
     */
    public static void clear() {
        update(null);
    }

    /**
     * In-memory state if this process wrote one, else the last state persisted on disk (restart case).
     */
    public static BasicDBObject read() {
        BasicDBObject inMemoryState = currentState;
        if (inMemoryState != null) {
            return inMemoryState;
        }
        return Utils.readJsonContentFromFile(Constants.TESTING_STATE_FOLDER_PATH, Constants.TESTING_STATE_FILE_NAME,
                BasicDBObject.class);
    }

    private static void persist(BasicDBObject state) {
        boolean persisted = Utils.writeJsonContentInFile(Constants.TESTING_STATE_FOLDER_PATH,
                Constants.TESTING_STATE_FILE_NAME, state);
        if (persisted) {
            persistenceFailureLogged = false;
            return;
        }
        if (!persistenceFailureLogged) {
            // logged once per failure streak, this runs on every state transition of every test run
            persistenceFailureLogged = true;
            loggerMaker.errorAndAddToDb("Unable to persist testing state at "
                    + Constants.TESTING_STATE_FOLDER_PATH + "/" + Constants.TESTING_STATE_FILE_NAME
                    + ". Continuing with in-memory state, the run cannot be resumed if this module restarts.");
        }
    }
}
