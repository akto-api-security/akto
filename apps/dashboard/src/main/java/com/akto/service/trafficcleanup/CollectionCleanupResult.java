package com.akto.service.trafficcleanup;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Outcome of evaluating (preview) or cleaning (delete) one API collection.
 *
 * <p>{@link #staleApis} is null — not 0 — whenever {@link #state} is FAILED: the count is unknown,
 * and a collection that could not be read must never look like a collection with nothing stale.
 * Consumers must check {@link #state} before reading the count.
 *
 * <p>{@link #deletedApis} is the number of APIs actually removed. It is 0 for a preview (nothing is
 * deleted), and for a FAILED delete it is the true number removed before the failure.
 */
@Getter
@Setter
@NoArgsConstructor
public class CollectionCleanupResult {

    public enum State { OK, FAILED }

    private int apiCollectionId;
    private String name;
    private State state;
    private Integer staleApis;
    private int deletedApis;
    private String error;

    public static CollectionCleanupResult ok(int apiCollectionId, String name, int staleApis, int deletedApis) {
        CollectionCleanupResult r = new CollectionCleanupResult();
        r.apiCollectionId = apiCollectionId;
        r.name = name;
        r.state = State.OK;
        r.staleApis = staleApis;
        r.deletedApis = deletedApis;
        return r;
    }

    public static CollectionCleanupResult failed(int apiCollectionId, String name, int deletedApis, String error) {
        CollectionCleanupResult r = new CollectionCleanupResult();
        r.apiCollectionId = apiCollectionId;
        r.name = name;
        r.state = State.FAILED;
        r.staleApis = null;
        r.deletedApis = deletedApis;
        r.error = error;
        return r;
    }
}
