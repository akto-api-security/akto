package com.akto.service.insights;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

/** One row of the Ask Akto overlay's "what changed" feed — computed on the fly per request, not
 *  read from the (stale, collection-unscoped) Activity feed. See InsightService.computeWhatChanged. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FeedItem {
    private String kind;          // NEW_API | NEW_ISSUE
    private String description;
    private String route;
    private Map<String, Object> params;
    private int timestamp;
}
