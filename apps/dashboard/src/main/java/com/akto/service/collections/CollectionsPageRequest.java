package com.akto.service.collections;

import lombok.Getter;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/** What the collections table asks for: one page of one tab, sorted and filtered. */
@Getter
public class CollectionsPageRequest {

    public static final int MAX_LIMIT = 100;
    public static final int DEFAULT_LIMIT = 50;

    private final int skip;
    private final int limit;
    /** the table's sort key, see CollectionsPageQueryBuilder.SORT_FIELDS */
    private final String sortKey;
    /** mongo style: 1 ascending, -1 descending */
    private final int sortOrder;
    /** a ApiCollectionStats.Tab name, or null / "ALL" for every collection */
    private final String tab;
    private final String queryValue;
    /** column filters: isOutOfTestingScope, accessType */
    private final Map<String, List<String>> filters;
    /** tag key -> accepted values; a collection must satisfy every key */
    private final Map<String, List<String>> tagFilters;
    private final boolean force;

    public CollectionsPageRequest(int skip, int limit, String sortKey, int sortOrder, String tab, String queryValue,
                                  Map<String, List<String>> filters, Map<String, List<String>> tagFilters, boolean force) {
        this.skip = Math.max(skip, 0);
        this.limit = limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
        this.sortKey = sortKey;
        this.sortOrder = sortOrder >= 0 ? 1 : -1;
        this.tab = tab;
        this.queryValue = queryValue;
        this.filters = filters == null ? Collections.emptyMap() : filters;
        this.tagFilters = tagFilters == null ? Collections.emptyMap() : tagFilters;
        this.force = force;
    }
}
