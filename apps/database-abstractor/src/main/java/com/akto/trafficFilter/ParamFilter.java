package com.akto.trafficFilter;

import java.util.ArrayList;
import java.util.List;

import com.akto.dao.context.Context;
import com.google.api.client.util.Charsets;
import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;

public class ParamFilter {

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

    public static boolean isNewEntry(int accountId, int apiCollectionId, String url, String method, String param) {
        String key = createKey(accountId, apiCollectionId, url, method, param);

        boolean isNew = true;
        refreshFilterList();
        for (BloomFilter<CharSequence> filter : filterList) {
            isNew &= (!filter.mightContain(key));
        }
        if (isNew) {
            insertInFilter(key);
            return true;
        }
        return false;
    }

}
