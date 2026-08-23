package com.akto.service.insights;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

/**
 * A deep-link descriptor only — the backend never performs the action. The frontend maps
 * kind + route + params to a link or a prefilled modal.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class InsightCta {
    private String id;                  // stable, e.g. "retire_policy"
    private String label;               // "Retire policy"
    private String kind;                // NAVIGATE | GUARDRAIL_TEMPLATE | BULK_ACTION
    private String route;               // e.g. "/dashboard/guardrails/policies"
    private Map<String, Object> params; // e.g. {"policyId": "..."}
    private boolean primary;
}
