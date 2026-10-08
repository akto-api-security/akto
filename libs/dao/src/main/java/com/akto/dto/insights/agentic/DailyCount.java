package com.akto.dto.insights.agentic;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** One {collection, day} vulnerability count, for AgentRedTeamFindingsProvider's per-agent trend
 *  (bucketed with PostureService.trendBucketBoundaries the same way shadowAiTrend is). `day` is
 *  floor(endTimestamp / 86400), computed in the aggregation, not in Java. */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class DailyCount {
    private int collectionId;
    private int day;
    private int count;
}
