package com.akto.service.trafficcleanup;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Dry-run response: how many APIs have had no traffic since {@link #cutoffEpoch}, per collection.
 *
 * <p>{@link #cutoffEpoch} is epoch seconds, fixed when the preview runs and passed back unchanged to
 * the delete so the two always agree on what "older than" means.
 *
 * <p>{@link #totalStaleApis} sums only collections in state OK. {@link #complete} is false whenever
 * any collection is not OK, in which case the total is a lower bound and must not be presented as
 * final. An empty {@link #collections} with complete=true means no collection was eligible, which
 * is different from a failed read.
 */
@Getter
@Setter
@NoArgsConstructor
public class TrafficRetentionPreview {

    private int days;
    private int cutoffEpoch;
    private boolean complete;
    private long totalStaleApis;
    private List<CollectionCleanupResult> collections = new ArrayList<>();

    public static TrafficRetentionPreview from(int days, int cutoffEpoch, List<CollectionCleanupResult> results) {
        TrafficRetentionPreview preview = new TrafficRetentionPreview();
        preview.days = days;
        preview.cutoffEpoch = cutoffEpoch;
        preview.collections = results;
        preview.complete = true;
        for (CollectionCleanupResult r : results) {
            if (r.getState() == CollectionCleanupResult.State.OK) {
                preview.totalStaleApis += r.getStaleApis();
            } else {
                preview.complete = false;
            }
        }
        return preview;
    }
}
