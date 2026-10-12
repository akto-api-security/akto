package com.akto.service.collections;

import com.akto.dto.ApiCollection;
import com.akto.dto.ApiCollectionStats;
import com.akto.dto.ApiCollectionStatsMeta;
import com.akto.dto.billing.UningestedApiOverage;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * What the collections table's two calls return. Serialized as is (Jackson reads the getters), so a
 * field here is a field of the json the table reads.
 */
public final class CollectionsPageResponse {

    private CollectionsPageResponse() {
    }

    /** One page of collections plus the per collection numbers the table's columns need for those rows. */
    @Getter
    @Setter
    public static class Page {
        private List<ApiCollection> apiCollections = new ArrayList<>();
        private long total;
        private Map<Integer, Double> riskScoreMap = Collections.emptyMap();
        private Map<Integer, Integer> lastSeenMap = Collections.emptyMap();
        private Map<Integer, List<String>> sensitiveInfoMap = Collections.emptyMap();
        /** collection id -> open issues by severity; empty with issuesUnavailable set when that query failed or ran out of time */
        private Map<Integer, Map<String, Integer>> severityInfoMap = Collections.emptyMap();
        private boolean issuesUnavailable;
        /** the rows of the untracked tab, which has no ApiCollection behind them */
        private List<UntrackedRow> untrackedRows = new ArrayList<>();
        /** metric -> epoch seconds of its last refresh, for the "updated Xs ago" label */
        private Map<String, Integer> statsUpdatedAt = Collections.emptyMap();
        private boolean statsPending;
    }

    /**
     * Tested apis (api_info) of the rows of one page: the one column that is not in the page response.
     * Fetched after the page so a slow query never holds the rows back; when it failed or ran out of
     * time it is reported unavailable rather than shown as zero.
     */
    @Getter
    public static class Details {
        private final Map<Integer, Integer> coverageMap;
        private final boolean coverageUnavailable;

        /** @param coverageMap null when the query failed or ran out of time */
        public Details(Map<Integer, Integer> coverageMap) {
            this.coverageUnavailable = coverageMap == null;
            this.coverageMap = coverageMap == null ? Collections.emptyMap() : coverageMap;
        }
    }

    /** The header of the table: tab badges, summary card, filter choices. */
    @Getter
    @AllArgsConstructor
    public static class Meta {
        private final TabCounts tabCounts;
        private final Summary summary;
        /** tag key -> its values */
        private final Map<String, List<String>> tagChoices;
        private final boolean hasUsageEndpoints;
        private final Map<String, Integer> statsUpdatedAt;
        private final boolean statsPending;
    }

    /** Named like the table's tab ids, so the table reads them directly. */
    @Getter
    public static class TabCounts {
        private final long all;
        private final long hostname;
        private final long groups;
        private final long custom;
        private final long deactivated;
        private final long untracked;

        public TabCounts(Map<ApiCollectionStats.Tab, Long> countByTab, long untracked) {
            this.hostname = countByTab.getOrDefault(ApiCollectionStats.Tab.HOSTNAME, 0L);
            this.groups = countByTab.getOrDefault(ApiCollectionStats.Tab.GROUP, 0L);
            this.custom = countByTab.getOrDefault(ApiCollectionStats.Tab.CUSTOM, 0L);
            this.deactivated = countByTab.getOrDefault(ApiCollectionStats.Tab.DEACTIVATED, 0L);
            this.all = hostname + groups + custom + deactivated;
            this.untracked = untracked;
        }
    }

    @Getter
    public static class Summary {
        private final int totalAllowedForTesting;
        private final int totalTestedEndpoints;
        private final int totalCriticalEndpoints;
        private final int totalSensitiveEndpoints;

        public Summary(ApiCollectionStatsMeta meta) {
            this.totalAllowedForTesting = meta.getTotalAllowedForTesting();
            this.totalTestedEndpoints = meta.getTotalTestedEndpoints();
            this.totalCriticalEndpoints = meta.getTotalCriticalEndpoints();
            this.totalSensitiveEndpoints = meta.getTotalSensitiveEndpoints();
        }
    }

    /** A collection that has apis seen but not ingested, with those apis. */
    @Getter
    @AllArgsConstructor
    public static class UntrackedRow {
        private final int id;
        private final String displayName;
        private final int startTs;
        private final int urlsCount;
        private final List<UntrackedApi> uningestedApiList;
    }

    @Getter
    @NoArgsConstructor
    public static class UntrackedApi {
        private int apiCollectionId;
        private String url;
        private String method;
        private String urlType;
        private int timestamp;

        public UntrackedApi(UningestedApiOverage api) {
            this.apiCollectionId = api.getApiCollectionId();
            this.url = api.getUrl();
            this.method = api.getMethod() == null ? null : api.getMethod().name();
            this.urlType = api.getUrlType();
            this.timestamp = api.getTimestamp();
        }
    }
}
