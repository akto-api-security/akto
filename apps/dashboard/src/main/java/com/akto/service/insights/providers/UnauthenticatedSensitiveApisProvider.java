package com.akto.service.insights.providers;

import com.akto.dto.ApiInfo;
import com.akto.dto.ApiInfo.ApiAccessType;
import com.akto.service.insights.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Unauthenticated APIs exposed — of the APIs whose only observed auth type is UNAUTHENTICATED,
 * how many are also reachable by the public/third parties, and of those, how many also carry
 * sensitive data. Ask Akto overlay's API_POSTURE group.
 */
public class UnauthenticatedSensitiveApisProvider extends AbstractInsightProvider {

    public UnauthenticatedSensitiveApisProvider() { super(InsightId.UNAUTHENTICATED_SENSITIVE_APIS, 1); }

    @Override
    public InsightResult compute(InsightDataBundle bundle, InsightContext ctx, Scope scope) {
        InsightResult r = skeleton();

        List<ApiInfo> rows = bundle.apiInfoRows();
        int total = rows.size();
        int unauthCount = 0;
        int unauthPublicCount = 0;
        int unauthPublicSensitiveCount = 0;

        List<Map<String, Object>> evidenceRows = new ArrayList<>();
        for (ApiInfo info : rows) {
            List<String> actualAuth = info.getActualAuthType();
            boolean onlyUnauthenticated = actualAuth != null && actualAuth.size() == 1
                    && ApiInfo.AuthType.UNAUTHENTICATED.equals(actualAuth.get(0));
            if (!onlyUnauthenticated) continue;
            unauthCount++;

            java.util.Set<ApiAccessType> accessTypes = info.getApiAccessTypes();
            boolean publicOrThirdParty = accessTypes != null &&
                    (accessTypes.contains(ApiAccessType.PUBLIC) || accessTypes.contains(ApiAccessType.THIRD_PARTY));
            if (!publicOrThirdParty) continue;
            unauthPublicCount++;

            boolean sensitive = info.getIsSensitive();
            if (sensitive) unauthPublicSensitiveCount++;

            if (evidenceRows.size() < 200) {
                Map<String, Object> row = new HashMap<>();
                row.put("collection", info.getId().getApiCollectionId());
                row.put("endpoint", info.getId().getUrl());
                row.put("method", String.valueOf(info.getId().getMethod()));
                row.put("accessType", info.findActualAccessType() != null ? info.findActualAccessType().name() : "");
                row.put("sensitive", sensitive);
                evidenceRows.add(row);
            }
        }

        r.addMetric(new InsightResult.Metric("totalApis", "Total APIs", total, "count", InsightUtil.count(total, "APIs")));
        r.addMetric(new InsightResult.Metric("unauthenticatedExposed", "Unauthenticated & public/third-party",
                unauthPublicCount, total, "count", InsightUtil.ofTotal(unauthPublicCount, total, "APIs"), null));
        r.addMetric(new InsightResult.Metric("unauthenticatedExposedSensitive", "...also carrying sensitive data",
                unauthPublicSensitiveCount, "count", InsightUtil.count(unauthPublicSensitiveCount, "APIs")));

        r.setMetricsComplete(!bundle.isApiInfoRowsTruncated());
        if (bundle.isApiInfoRowsTruncated()) {
            r.addDataGap(new InsightResult.Gap("API_INFO", "REQUEST_FAILED",
                    "The api_info scan was capped before finishing — this count is a lower bound, not the full account."));
        }

        r.setStatus((total == 0 ? InsightResult.Status.NO_DATA : InsightResult.Status.READY).name());
        r.setHeadline(unauthPublicCount == 0 ? "No unauthenticated APIs exposed to the public"
                : InsightUtil.count(unauthPublicCount, "unauthenticated APIs") + " are reachable by the public or third parties");

        if (unauthPublicCount > 0) {
            if (unauthPublicSensitiveCount > 0) {
                r.setSeverity("CRITICAL");
            } else {
                r.setSeverity("HIGH");
            }
            r.setConcern(InsightUtil.count(unauthPublicCount, "APIs") + " require no authentication at all and are reachable "
                    + "by the public or third parties" + (unauthPublicSensitiveCount > 0
                    ? ", and " + InsightUtil.count(unauthPublicSensitiveCount, "of them") + " also return sensitive data."
                    : "."));
            r.setImpact("An unauthenticated, publicly-reachable API is the lowest-effort path to a real incident — "
                    + "anyone who finds the URL can call it.");
            r.setRemediation("Start with the ones also carrying sensitive data, then work through the rest by exposure.");
        } else if (unauthCount > 0) {
            r.setSeverity("MEDIUM");
            r.setConcern(InsightUtil.count(unauthCount, "APIs") + " require no authentication, though none are currently marked public or third-party facing.");
        }

        r.addEvidence(new InsightResult.Evidence("unauthExposed", "Unauthenticated & exposed APIs",
                java.util.Arrays.asList("collection", "endpoint", "method", "accessType", "sensitive"),
                evidenceRows, unauthPublicCount));

        Map<String, Object> ctaParams = new HashMap<>();
        ctaParams.put("authType", java.util.Collections.singletonList("UNAUTHENTICATED"));
        r.addCta(new InsightResult.Cta("view_inventory", "Review in inventory", "NAVIGATE", InsightRoutes.INVENTORY, ctaParams, true));
        return r;
    }
}
