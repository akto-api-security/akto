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
    private final static int FILTER_UPDATE_DURATION = 5 * 60;
    public final static int FULL_RESET_DURATION_MINUTES = 5;
    private static boolean currentlyCleaning = false;

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
            BloomFilter<CharSequence> newFilter = BloomFilter.create(Funnels.stringFunnel(Charsets.UTF_8), 5_000_000,
                    0.001);
            lastUpdateTs = now;
            filterId = (filterId + 1) % TOTAL_FILTERS;
            if(!currentlyCleaning){
                if (filterId < filters.size()) {
                    filters.set(filterId, newFilter);
                } else {
                    filters.add(newFilter);
                }
            }
        }
    }

    public static boolean isEligibleForUpdate(int apiCollectionId, String url, String method, String param, int responseCode, String operation) {
        // If reset is in progress, allow the update to proceed (conservative approach)
        if (currentlyCleaning) {
            return true;
        }

        BloomFilter<CharSequence> filter;
        int checkIdx;
        boolean found = false;
        // assigns correct filter. Creates a new filter if filter at that index is not present
        assignFilterForOperation();
        String key = buildKey(apiCollectionId, url, method, param, responseCode, operation);
        if(key == null) {
            return true;
        }
        for (int i = 0; i < TOTAL_FILTERS; i++) {
            checkIdx = (filterId + i) % TOTAL_FILTERS;
            filter = filters.get(checkIdx);
            if (!currentlyCleaning && filter != null && filter.mightContain(key)) {
                found = true;
                break;
            }
        }

        addKeyToFilter(key);
        return !found;
    }

    public static void addKeyToFilter(String key) {
        // Skip adding to filter if reset is in progress
        if (filters.get(filterId) == null || currentlyCleaning || key == null) {
            return;
        }
        filters.get(filterId).put(key);
    }

    public static String buildKey(int apiCollectionId, String url, String method, String param, int responseCode, String operation) {
        return apiCollectionId + "$" + url + "$" + method + "$" + param +  "$" + responseCode + "$" + operation;
    }

    public static synchronized void resetAllFilters() {
        try {
            currentlyCleaning = true;
            filters.clear();
            for (int i = 0; i < TOTAL_FILTERS; i++) {
                filters.add(BloomFilter.create(Funnels.stringFunnel(Charsets.UTF_8), 5_000_000, 0.001));
            }
            filterId = -1;
            currentlyCleaning = false;
        } catch (Exception e) {
            e.printStackTrace();
            currentlyCleaning = false;
        } finally {
            currentlyCleaning = false;
        }
    }
}
