package com.akto.service.ask;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

/**
 * A lightweight, always-on tile for the Ask Akto overlay — one live count and one prompt it
 * fires when clicked. Deliberately NOT an InsightResult: it never touches InsightDataBundle, and
 * the count comes from a single bounded COUNT query, not the full insights compute path. See
 * RecommendationCatalog.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Recommendation {
    private String id;
    private String label;
    private long count;
    private String unit;              // "count"
    private String severity;          // nullable — a plain fact tile has none
    private String promptTemplate;    // %d is replaced with count by the caller
    private String route;
    private Map<String, Object> params;
}
