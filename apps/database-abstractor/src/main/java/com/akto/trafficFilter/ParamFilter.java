package com.akto.trafficFilter;

import java.util.ArrayList;
import java.util.List;

import com.akto.dao.context.Context;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.google.api.client.util.Charsets;
import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;

public class ParamFilter {
    private static final LoggerMaker loggerMaker = new LoggerMaker(ParamFilter.class, LogDb.DB_ABS);

    private static List<BloomFilter<CharSequence>> filterList = new ArrayList<>();
    private static int currentFilterIndex = -1;
    private static int filterFillStart = 0;
    private static final int TIME_LIMIT = 5 * 60;
    private static final int FILTER_LIMIT = 5;
    private static final String DOLLAR = "$";

    private static void insertInFilter(String key) {
        filterList.get(currentFilterIndex).put(key);
    }

    private static void refreshFilterList() {
        int now = Context.now();

        if ((filterFillStart + TIME_LIMIT) < now) {
            filterFillStart = now;
            currentFilterIndex++;
            currentFilterIndex %= FILTER_LIMIT;
            BloomFilter<CharSequence> newFilter = BloomFilter.create(Funnels.stringFunnel(Charsets.UTF_8), 1_000_000,
                    0.001);
            if (currentFilterIndex < filterList.size()) {
                filterList.set(currentFilterIndex, newFilter);
            } else {
                filterList.add(newFilter);
            }
        }
    }

    private static String createKey(int accountId, int apiCollectionId, String url, String method, String param) {
        return accountId + DOLLAR + apiCollectionId + DOLLAR + url + DOLLAR + method + DOLLAR + param;
    }

    private static int hits = 0;
    private static int misses = 0;
    private static int firstPrintTime = 0;
    private static final int PRINT_INTERVAL = 60;
    private static final int DEBUG_COUNT = 50;

    private static void printL(Object o, boolean isHit) {
        int now = Context.now();
        if ((firstPrintTime + PRINT_INTERVAL) < now) {
            loggerMaker.infoAndAddToDb(String.format("ParamFilter hits: %d , misses: %d , firstPrintTime: %d , now : %d",hits, misses, firstPrintTime, now));
            firstPrintTime = now;
            hits = 0;
            misses = 0;
        }
        if (isHit) {
            hits++;
        } else {
            misses++;
        }
        if ((isHit && hits < DEBUG_COUNT) || (!isHit && misses < DEBUG_COUNT)) {
            loggerMaker.infoAndAddToDb(o.toString());
        }
    }

    public static boolean isNewEntry(int accountId, int apiCollectionId, String url, String method, String param) {
        String key = createKey(accountId, apiCollectionId, url, method, param);

        boolean isNew = true;
        refreshFilterList();
        for (BloomFilter<CharSequence> filter : filterList) {
            isNew &= (!filter.mightContain(key));
        }
        if (isNew) {
            printL("ParamFilter inserting: " + key, false);
            insertInFilter(key);
            return true;
        }
        printL("ParamFilter skipping: " + key, true);
        return false;
    }

}
