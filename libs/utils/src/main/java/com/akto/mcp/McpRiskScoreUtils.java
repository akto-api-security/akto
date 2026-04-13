package com.akto.mcp;

import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.McpAuditInfoDao;
import com.akto.dao.context.Context;
import com.akto.dto.ApiCollection;
import com.akto.dto.ComponentRiskAnalysis;
import com.akto.dto.McpAuditInfo;
import com.akto.dto.traffic.CollectionTags;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.Constants;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import org.apache.commons.lang3.StringUtils;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * MCP overlay (malicious server tag or malicious component in audits: {@link #MCP_MALICIOUS_INTENT_OVERLAY};
 * privileged capability: up to 1). Dashboard merges into {@link com.akto.dto.ApiInfo#riskScore} as
 * {@code Math.min(MAX_MCP_RISK_SCORE, base + max(0, overlay))} after {@link com.akto.dao.ApiInfoDao#getRiskScore}.
 */
public final class McpRiskScoreUtils {

    private static final LoggerMaker logger = new LoggerMaker(McpRiskScoreUtils.class, LogDb.DASHBOARD);

    public static final float MAX_MCP_RISK_SCORE = 5f;

    /** Overlay points when the collection is tagged malicious MCP or any audit marks the component malicious. */
    public static final float MCP_MALICIOUS_SCORE = 3f;

    private McpRiskScoreUtils() {}

    /**
     * @param hasMaliciousMcpServerTag true when the API collection is tagged as a malicious MCP server
     */
    public static float computeRiskOverlayFromMcpAudits(
        List<McpAuditInfo> audits,
        boolean hasMaliciousMcpServerTag
    ) {
        boolean maliciousIntent = hasMaliciousMcpServerTag;
        float privilegedAccessBonus = 0f;
        if (audits != null) {
            for (McpAuditInfo audit : audits) {
                if (audit == null) {
                    continue;
                }
                ComponentRiskAnalysis cra = audit.getComponentRiskAnalysis();
                if (cra != null && cra.getIsComponentMalicious()) {
                    maliciousIntent = true;
                }
                privilegedAccessBonus = Math.max(
                    privilegedAccessBonus,
                    overlayContributionFromPrivilegedAccess(cra)
                );
            }
        }
        float raw = (maliciousIntent ? MCP_MALICIOUS_SCORE : 0f) + privilegedAccessBonus;
        return Math.min(MAX_MCP_RISK_SCORE, raw);
    }

    private static float overlayContributionFromPrivilegedAccess(ComponentRiskAnalysis cra) {
        if (cra != null && cra.getHasPrivilegedAccess()) {
            return 1f;
        }
        return 0f;
    }

    public static Map<Integer, Float> computeRiskOverlaysByApiCollectionId() {
        Map<Integer, Float> out = new HashMap<>();
        Integer acc = Context.accountId.get();
        Bson mcpTagFilter = Filters.elemMatch(
            ApiCollection.TAGS_STRING,
            Filters.eq(CollectionTags.KEY_NAME, Constants.AKTO_MCP_SERVER_TAG)
        );
        Bson activeFilter = Filters.or(
            Filters.exists(ApiCollection._DEACTIVATED, false),
            Filters.eq(ApiCollection._DEACTIVATED, false)
        );
        List<ApiCollection> cols = ApiCollectionsDao.instance.findAll(
            Filters.and(mcpTagFilter, activeFilter),
            Projections.include(ApiCollection.ID, ApiCollection.HOST_NAME, ApiCollection.TAGS_STRING)
        );
        if (cols == null || cols.isEmpty()) {
            return out;
        }
        int mcpCollectionRows = 0;
        Set<String> hosts = new HashSet<>();
        for (ApiCollection c : cols) {
            if (!c.isMcpCollection()) {
                continue;
            }
            mcpCollectionRows++;
            String h = McpRequestResponseUtils.extractServiceNameFromHost(c.getHostName());
            if (StringUtils.isNotBlank(h)) {
                hosts.add(h);
            }
        }
        if (hosts.isEmpty()) {
            if (mcpCollectionRows > 0) {
                logger.warn(
                    "MCP risk score util: account={} {} mcp-tagged collections but host key empty after extractServiceNameFromHost — overlays stay 0",
                    acc,
                    mcpCollectionRows
                );
            }
            return out;
        }
        List<McpAuditInfo> allAudits = McpAuditInfoDao.instance.findAll(
            Filters.in(McpAuditInfo.MCP_HOST, hosts),
            0,
            5_000,
            null
        );
        int auditDocCount = allAudits == null ? 0 : allAudits.size();
        Map<String, List<McpAuditInfo>> byHost = new HashMap<>();
        if (allAudits != null) {
            for (McpAuditInfo a : allAudits) {
                if (a == null || StringUtils.isBlank(a.getMcpHost())) {
                    continue;
                }
                byHost.computeIfAbsent(a.getMcpHost(), k -> new ArrayList<>()).add(a);
            }
        }
        int positiveOverlayCollections = 0;
        for (ApiCollection c : cols) {
            if (!c.isMcpCollection()) {
                continue;
            }
            String h = McpRequestResponseUtils.extractServiceNameFromHost(c.getHostName());
            if (StringUtils.isBlank(h)) {
                continue;
            }
            List<McpAuditInfo> aud = byHost.getOrDefault(h, Collections.emptyList());
            boolean maliciousTag = apiCollectionHasMaliciousMcpServerTag(c);
            float overlay = computeRiskOverlayFromMcpAudits(aud, maliciousTag);
            out.put(c.getId(), overlay);
            if (overlay > 0f) {
                positiveOverlayCollections++;
            }
        }
        if (!hosts.isEmpty() && auditDocCount == 0) {
            logger.warn(
                "MCP risk score util: account={} {} host key(s) for audit lookup but 0 McpAuditInfo rows — check mcp_host vs extractServiceNameFromHost",
                acc,
                hosts.size()
            );
        } else if (mcpCollectionRows > 0 && positiveOverlayCollections == 0 && auditDocCount > 0) {
            logger.warn(
                "MCP risk score util: account={} {} mcp collections, {} audit doc(s) but overlay 0 everywhere — check malicious-mcp-server tag and ComponentRiskAnalysis malicious/privileged on audits",
                acc,
                mcpCollectionRows,
                auditDocCount
            );
        }
        return out;
    }

    private static boolean apiCollectionHasMaliciousMcpServerTag(ApiCollection collection) {
        List<CollectionTags> tags = collection.getTagsList();
        if (tags == null) {
            return false;
        }
        for (CollectionTags tag : tags) {
            if (Constants.AKTO_MALICIOUS_MCP_SERVER_TAG.equals(tag.getKeyName())
                && "true".equalsIgnoreCase(tag.getValue())) {
                return true;
            }
        }
        return false;
    }
}
