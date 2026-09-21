package com.akto.service.insights;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * One {apiCollectionId, url, method, testSubType} finding's recurrence across test runs — built
 * by InsightLazySources.issueRecurrence(). Flat fields rather than a reconstructed
 * ApiInfoKey: the aggregation only needs to compare/report identity, not round-trip a typed key.
 *
 * distinctRuns is a count of distinct testRunResultSummaryIds ($addToSet in the aggregation),
 * never a raw document count — one summary can hold more than one result row for the same key
 * (reruns), which would otherwise overstate "how many test runs saw this".
 */
@Getter
@AllArgsConstructor
public class IssueRecurrenceRow {
    private final int apiCollectionId;
    private final String url;
    private final String method;
    private final String testSubType;
    private final int distinctRuns;
    private final int firstSeen;
    private final int lastSeen;
}
