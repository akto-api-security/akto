package com.akto.service.insights;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * What one insight card renders on the Ask Akto overlay — nothing more. Deliberately a narrower
 * type than InsightResult, not InsightResult with some fields nulled out: toTile() (see
 * InsightService) never reads getEvidence()/getMarkdown()/getNarrativeInput(), so those aren't
 * merely absent from the JSON, they are structurally unreachable. A nulling convention is a rule
 * a human has to remember on every future InsightResult field; a narrower type is one the
 * compiler enforces.
 */
@Getter
@Setter
@NoArgsConstructor
public class InsightTile {
    private String insightId;
    private String title;
    private String group;
    private String category;
    private String status;
    private String severity;
    private String headline;
    private boolean disabled;
    private boolean metricsComplete;
    private int dataGapCount;
    private List<InsightResult.Metric> metrics = new ArrayList<>();
    private InsightResult.Cta primaryCta;
}
