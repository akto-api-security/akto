package com.akto.hybrid_runtime.filter_updates;

import com.akto.dao.context.Context;
import com.google.api.client.util.Charsets;
import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;

import java.util.ArrayList;
import java.util.List;

public class FilterUpdates {

    private static int filterId = -1;
    private static int TOTAL_FILTERS = 5;
    private static int lastUpdateTs = 0;
    private static int FILTER_UPDATE_DURATION = 5 * 60;

    private static List<BloomFilter<CharSequence>> filters = new ArrayList<BloomFilter<CharSequence>>() {
        {
            add(BloomFilter.create(Funnels.stringFunnel(Charsets.UTF_8), 5_000_000, 0.001));
            add(BloomFilter.create(Funnels.stringFunnel(Charsets.UTF_8), 5_000_000, 0.001));
            add(BloomFilter.create(Funnels.stringFunnel(Charsets.UTF_8), 5_000_000, 0.001));
            add(BloomFilter.create(Funnels.stringFunnel(Charsets.UTF_8), 5_000_000, 0.001));
            add(BloomFilter.create(Funnels.stringFunnel(Charsets.UTF_8), 5_000_000, 0.001));
        }
    };

    private static synchronized void assignFilterForOperation() {
        int now = Context.now();
        if ((lastUpdateTs + FILTER_UPDATE_DURATION) < now) {
            BloomFilter<CharSequence> newFilter = BloomFilter.create(Funnels.stringFunnel(Charsets.UTF_8), 1_000_000,
                    0.001);
            lastUpdateTs = now;
            filterId = (filterId + 1) % TOTAL_FILTERS;
            if (filterId < filters.size()) {
                filters.set(filterId, newFilter);
            } else {
                filters.add(newFilter);
            }
        }
    }

    public static boolean isEligibleForUpdate(int apiCollectionId, String url, String method, String param, int responseCode, String operation) {
        BloomFilter<CharSequence> filter;
        int checkIdx;
        boolean found = false;
        // assigns correct filter. Creates a new filter if filter at that index is not present
        assignFilterForOperation();
        String key = buildKey(apiCollectionId, url, method, param, responseCode, operation);
        for (int i = 0; i < TOTAL_FILTERS; i++) {
            checkIdx = (filterId + i) % TOTAL_FILTERS;
            filter = filters.get(checkIdx);
            if (filter.mightContain(key)) {
                found = true;
                break;
            }
        }

        addKeyToFilter(key);
        return !found;
    }

    public static void addKeyToFilter(String key) {
        filters.get(filterId).put(key);
    }

    public static String buildKey(int apiCollectionId, String url, String method, String param, int responseCode, String operation) {
        return apiCollectionId + "$" + url + "$" + method + "$" + param +  "$" + responseCode + "$" + operation;
    }

}
