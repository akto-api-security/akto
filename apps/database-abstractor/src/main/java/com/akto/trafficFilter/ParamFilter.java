package com.akto.trafficFilter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.akto.dao.context.Context;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.google.api.client.util.Charsets;
import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;

public class ParamFilter {
    private static final LoggerMaker loggerMaker = new LoggerMaker(ParamFilter.class, LogDb.DB_ABS);

    private static List<BloomFilter<CharSequence>> filterList = new ArrayList<>();
    private static BloomFilter<CharSequence> hostFilter = BloomFilter.create(Funnels.stringFunnel(Charsets.UTF_8), 1_000_000,0.001);
    private static int currentFilterIndex = -1;
    private static int filterFillStart = 0;
    private static final int TIME_LIMIT = 5 * 60;
    private static final int FILTER_LIMIT = 5;
    private static final String DOLLAR = "$";
    private static final String HOST = "host";

    private static void insertInFilter(String key) {
        filterList.get(currentFilterIndex).put(key);
    }
    private static Map<Integer, Integer> filterListHitCount = new HashMap<>();

    private static void refreshFilterList() {
        int now = Context.now();

        if ((filterFillStart + TIME_LIMIT) < now) {
            filterFillStart = now;
            currentFilterIndex++;
            currentFilterIndex %= FILTER_LIMIT;
            BloomFilter<CharSequence> newFilter = BloomFilter.create(Funnels.stringFunnel(Charsets.UTF_8), 1_000_000,
                    0.001);
            
            String hitCountLog = "";
            for (int i = 0; i < FILTER_LIMIT; i++) {
                hitCountLog += filterListHitCount.getOrDefault(i, 0) + " ";
            }
            loggerMaker.infoAndAddToDb(String.format("ParamFilter hitCounts: %s",hitCountLog));

            if (currentFilterIndex < filterList.size()) {
                filterList.set(currentFilterIndex, newFilter);
            } else {
                filterList.add(newFilter);
            }
            filterListHitCount.put(currentFilterIndex, 0);
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
    private static int hostHits = 0;
    private static int hostMisses = 0;

    private static void printL(Object o, boolean print) {
        int now = Context.now();
        if (print) {
            loggerMaker.infoAndAddToDb(o.toString());
        }
        if ((firstPrintTime + PRINT_INTERVAL) < now) {
            loggerMaker.infoAndAddToDb(String.format("ParamFilter hits: %d , misses: %d , hostHits %d, hostMisses %d, firstPrintTime: %d , now : %d",hits, misses, hostHits, hostMisses, firstPrintTime, now));
            firstPrintTime = now;
            hits = 0;
            misses = 0;
            hostHits = 0;
            hostMisses = 0;
        }
    }

    public static boolean isNewEntry(int accountId, int apiCollectionId, String url, String method, String param) {
        String key = createKey(accountId, apiCollectionId, url, method, param);

        if (HOST.equals(param.toLowerCase())) {
            if (!hostFilter.mightContain(key)) {
                hostMisses++;
                printL("ParamFilter inserting host: " + key, hostMisses < DEBUG_COUNT);
                // return true;
            } else {
                hostHits++;
                printL("ParamFilter skipping host: " + key, hostHits < DEBUG_COUNT);
                // return false;
            }
            hostFilter.put(key);
            // to make it no-op.
            return true;
        }

        boolean isNew = true;
        refreshFilterList();
        int i=0;
        for (BloomFilter<CharSequence> filter : filterList) {
            boolean notFound = (!filter.mightContain(key));
            isNew &= notFound;
            if(!notFound){
                int temp = filterListHitCount.getOrDefault(i, 0)+1;
                filterListHitCount.put(i, temp);
            }
            i++;
        }
        if (isNew) {
            misses++;
            printL("ParamFilter inserting: " + key, misses < DEBUG_COUNT);
            insertInFilter(key);
            // return true;
        }else {
            hits++;
            printL("ParamFilter skipping: " + key, hits < DEBUG_COUNT);
        }
        // return false;

        // to make it no-op.
        return true;
    }

}