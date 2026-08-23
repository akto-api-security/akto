package com.akto.service.insights;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Non-empty on an InsightResult iff its status is PARTIAL. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DataGap {
    private String source; // e.g. THREAT_BACKEND | POLICY_STORE | PROVIDER
    private String reason; // e.g. NOT_CONFIGURED | REQUEST_FAILED | NO_ROWS | DEFERRED_TO_DETAIL
    private String impact; // rendered verbatim in the narrative's closing line
}
