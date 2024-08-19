package com.akto.action.observe;

import com.akto.action.UserAction;
import com.akto.log.LoggerMaker;

public class ApiInventoryQueryAction extends UserAction {

    private static final LoggerMaker loggerMaker = new LoggerMaker(ApiInventoryQueryAction.class, LoggerMaker.LogDb.DASHBOARD);
    int[] collectionIds;

    public enum FilterQueryType {
        ENDPOINT,
        METHOD,
        PARAMETER,
        TIMESTAMP
    }
}