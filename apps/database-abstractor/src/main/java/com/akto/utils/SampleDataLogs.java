package com.akto.utils;

import com.akto.dao.context.Context;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import java.util.HashMap;
import java.util.Map;

public class SampleDataLogs {

    static Map<String, Integer> countMap = new HashMap<>();
    private static final LoggerMaker logger = new LoggerMaker(SampleDataLogs.class, LogDb.DB_ABS);

    public static int modVal() {
        String val = System.getenv("MOD_VAL");
        try {
            int i = Integer.valueOf(val);
            return i;
        } catch (Exception e) {
        }
        return 10;
    }

    public static String createKey(int apiCollectionId, String method, String url) {
        int accountId = Context.accountId.get();
        String q = String.format("%d %d %s %s", accountId, apiCollectionId, method, url);
        return q;
    }

    public static void insertCount(int apiCollectionId, String method, String url, int c) {
        String q = createKey(apiCollectionId, method, url);
        int count = 0;
        if (countMap.containsKey(q)) {
            count = countMap.get(q) + c;
        }
        countMap.put(q, count);
    }

    public static void printLog(int apiCollectionId, String method, String url) {
        String q = createKey(apiCollectionId, method, url);
        if (countMap.containsKey(q)) {
            int count = countMap.get(q);
            if (count % modVal() == 0) {
                logger.info(String.format("%s count : %d", q, count));
            }
        }
    }
}
