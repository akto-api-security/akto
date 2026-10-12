package com.akto.service.collections;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** One page of an action item's list. */
@Getter
@AllArgsConstructor
public class PostureActionItemsResponse {

    private final List<Row> rows;
    /** the test sub categories of the "common vulnerabilities" action item; rows is empty then */
    private final List<SubCategory> subCategories;
    /** every row of the action item, -1 when this account's data cannot tell */
    private final long total;

    static PostureActionItemsResponse ofRows(List<Row> rows, long total) {
        return new PostureActionItemsResponse(rows, new ArrayList<>(), total);
    }

    /** The columns the flyout tables show, whichever collection the api came from. */
    @Getter
    @AllArgsConstructor
    public static class Row {
        private final int apiCollectionId;
        private final String url;
        private final String method;
        private final float riskScore;
        private final Set<String> apiAccessTypes;
        /** api_info's sets of auth types found, or null for a row read from the view */
        private final Set<Set<String>> allAuthTypesFound;
        /** the view's actual auth types, or null for a row read from api_info */
        private final List<String> actualAuthType;
        private final int lastSeen;
        private final int discoveredTimestamp;
        private final int lastTested;
        /** the sub categories of the open issues of the api, for the "multiple issues" action item */
        private final List<String> issueNames;

        Row withIssueNames(List<String> names) {
            return new Row(apiCollectionId, url, method, riskScore, apiAccessTypes, allAuthTypesFound, actualAuthType,
                    lastSeen, discoveredTimestamp, lastTested, names);
        }
    }

    @Getter
    @AllArgsConstructor
    public static class SubCategory {
        private final String testSubCategory;
        private final long endpointsCount;
        /** the first endpoints only */
        private final List<Endpoint> endpoints;
    }

    @Getter
    @AllArgsConstructor
    public static class Endpoint {
        private final int apiCollectionId;
        private final String url;
        private final String method;
    }
}
