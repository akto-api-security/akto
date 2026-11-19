package com.akto.service.insights;

import com.akto.service.ask.Recommendation;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/** The whole Ask Akto overlay payload — one round trip for the command palette's landing state. */
@Getter
@Setter
public class AskOverlayResponse {
    private List<Recommendation> recommendations = new ArrayList<>();
    private List<InsightTile> insightTiles = new ArrayList<>();
    private List<FeedItem> whatChanged = new ArrayList<>();
    // Groups the caller's role/entitlement excludes — reported explicitly so the overlay can
    // render "you don't have access to Testing insights" instead of an unexplained gap, and so
    // "omitted" is never confused with "computed and empty". See InsightService.groupVisible.
    private List<String> omittedGroups = new ArrayList<>();
    private long generatedAt;
}
