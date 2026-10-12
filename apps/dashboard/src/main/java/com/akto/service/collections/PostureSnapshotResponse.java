package com.akto.service.collections;

import com.akto.dto.ApiStats;
import com.akto.service.collections.CollectionsPageResponse.NewDomain;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/** What the home dashboard's api counts call returns: charts, action item counts, missing data numbers. */
@Getter
@AllArgsConstructor
public class PostureSnapshotResponse {

    private final ApiStats apiStatsStart;
    private final ApiStats apiStatsEnd;
    /** true when the start of the range could not be worked out cheaply, so apiStatsStart is empty */
    private final boolean startUnavailable;
    private final ActionItemCounts actionItemCounts;
    private final Missing missing;
    /** metric -> epoch seconds of its last refresh, for the "updated Xs ago" label */
    private final Map<String, Integer> statsUpdatedAt;
    /** true while some collections have not been counted yet, so the numbers are still growing */
    private final boolean statsPending;
    /** the newest host collections, for the new domains tile */
    private final List<NewDomain> newDomains;

    /** The counts behind the action items; one the source cannot work out is -1. */
    @Getter
    @AllArgsConstructor
    public static class ActionItemCounts {
        private final long sensitiveAndUnauthenticated;
        private final long highRiskThirdParty;
        private final long shadowApis;
        private final long unauthenticated;
        private final long notTested;
        private final long onlyOnceTested;
        private final long highRisk;
        private final long sensitiveApis;
    }

    /** What the missing data charts need. */
    @Getter
    @AllArgsConstructor
    public static class Missing {
        /** apis seen in traffic that api_info does not have, less the api_info ones traffic does not have */
        private final long netMissing;
        private final long authNotCalculated;
        private final long accessTypeNotCalculated;
        private final long apiTypeMissing;
    }

    static final Map<String, Integer> NO_STATS = Collections.emptyMap();
}
