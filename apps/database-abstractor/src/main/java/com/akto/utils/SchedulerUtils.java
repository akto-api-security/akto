package com.akto.utils;

import java.util.HashMap;

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import com.akto.dao.context.Context;

public class SchedulerUtils {

    static Map<Integer, ScheduledExecutorService> schedulerMap = new HashMap<>();

    public static ScheduledExecutorService getService() {
        int accountId = Context.accountId.get();
        if (schedulerMap.containsKey(accountId)) {
            return schedulerMap.get(accountId);
        }
        final ScheduledExecutorService service = Executors.newSingleThreadScheduledExecutor();
        schedulerMap.put(accountId, service);
        return schedulerMap.get(accountId);
    }

}
