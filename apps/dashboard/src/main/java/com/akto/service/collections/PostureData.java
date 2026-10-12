package com.akto.service.collections;

import com.akto.dto.ApiStats;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** What a {@link PostureSource} knows about the apis of an account for a time range. */
@Getter
@AllArgsConstructor
final class PostureData {
    private final ApiStats start;
    private final ApiStats end;
    /** true when the start of the range could not be worked out cheaply, so start is empty */
    private final boolean startUnavailable;
    /** sums keyed by PostureMetric field (and PostureCounts extras) behind the action items and missing data */
    private final PostureCounts counts;
    /** apis in traffic but not in api_info, less the other way round */
    private final long netMissing;
    /** true while some collections have not been counted yet */
    private final boolean pending;
    /** how many collections have been counted, of how many; 0 of 0 where nothing is counted ahead (the view) */
    private final long countedCollections;
    private final long collections;
    /** true when the end of the range is in the past and too many apis were discovered since to take out of the end numbers: they are today's */
    private final boolean endApproximate;
}
