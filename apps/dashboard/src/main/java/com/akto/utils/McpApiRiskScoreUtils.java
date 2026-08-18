package com.akto.utils;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bson.conversions.Bson;

import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.McpAuditInfoDao;
import com.akto.dao.context.Context;
import com.akto.dto.ApiCollection;
import com.akto.dto.ApiInfo;
import com.akto.dto.ComponentRiskAnalysis;
import com.akto.dto.McpAuditInfo;
import com.akto.dto.traffic.CollectionTags;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.Constants;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;

public final class McpApiRiskScoreUtils {

    private McpApiRiskScoreUtils() {}

    private static final LoggerMaker loggerMaker = new LoggerMaker(McpApiRiskScoreUtils.class, LogDb.DASHBOARD);

    /** Extra risk when MCP audit marks the component malicious (capped with {@link #MCP_RISK_SCORE_CAP}). */
    private static final float MCP_COMPONENT_MALICIOUS_SCORE_DELTA = 3f;
    /** Extra risk when MCP audit marks privileged access (capped with {@link #MCP_RISK_SCORE_CAP}). */
    private static final float MCP_COMPONENT_PRIVILEGE_SCORE_DELTA = 1f;
    /** Max risk after base + MCP audit deltas (aligns with {@link com.akto.dto.testing.RiskScoreTestingEndpoints}). */
    private static final float MCP_RISK_SCORE_CAP = 5f;
    /** Floor when collection is tagged {@link Constants#AKTO_MALICIOUS_MCP_SERVER_TAG}. */
    private static final float MCP_MALICIOUS_SERVER_COLLECTION_RISK_FLOOR = 3f;

    private static final List<String> MCP_AUDIT_TYPES = Arrays.asList(
            Constants.AKTO_MCP_TOOLS_TAG,
            Constants.AKTO_MCP_RESOURCES_TAG,
            Constants.AKTO_MCP_PROMPTS_TAG,
            Constants.AKTO_MCP_TOOL,
            Constants.AKTO_MCP_RESOURCE,
            Constants.AKTO_MCP_PROMPT);

    public static final class McpRiskScoreContext {
        public final Set<Integer> mcpServerCollectionIds;
        public final Set<Integer> maliciousMcpServerCollectionIds;
        public final Map<String, ComponentRiskAnalysis> componentRiskByTypeAndResource;
        /** ApiCollection.baseRiskScore (0-2, AI agent risk) by collection id - independent of MCP tagging. */
        public final Map<Integer, Double> agentBaseRiskScoreByCollectionId;

        McpRiskScoreContext(Set<Integer> mcpServerCollectionIds,
                Set<Integer> maliciousMcpServerCollectionIds,
                Map<String, ComponentRiskAnalysis> componentRiskByTypeAndResource,
                Map<Integer, Double> agentBaseRiskScoreByCollectionId) {
            this.mcpServerCollectionIds = mcpServerCollectionIds;
            this.maliciousMcpServerCollectionIds = maliciousMcpServerCollectionIds;
            this.componentRiskByTypeAndResource = componentRiskByTypeAndResource;
            this.agentBaseRiskScoreByCollectionId = agentBaseRiskScoreByCollectionId;
        }

        public boolean isEmpty() {
            return mcpServerCollectionIds.isEmpty() && agentBaseRiskScoreByCollectionId.isEmpty();
        }
    }

    public static McpRiskScoreContext buildMcpRiskScoreContext() {
        Bson mcpTag = Filters.elemMatch(ApiCollection.TAGS_STRING,
                Filters.eq(CollectionTags.KEY_NAME, Constants.AKTO_MCP_SERVER_TAG));
        List<ApiCollection> cols = ApiCollectionsDao.instance.findAll(mcpTag,
                Projections.include(ApiCollection.ID, ApiCollection.TAGS_STRING));
        if (cols == null) {
            cols = Collections.emptyList();
        }
        Set<Integer> mcpIds = new HashSet<>();
        Set<Integer> maliciousIds = new HashSet<>();
        for (ApiCollection c : cols) {
            mcpIds.add(c.getId());
            if (collectionHasMaliciousMcpServerTag(c)) {
                maliciousIds.add(c.getId());
            }
        }

        Map<String, ComponentRiskAnalysis> map = new HashMap<>();
        int auditRowsSkippedNoCra = 0;
        int auditRowsLoaded = 0;
        if (!mcpIds.isEmpty()) {
            Bson auditFilter = Filters.and(
                    Filters.in(McpAuditInfo.TYPE, MCP_AUDIT_TYPES),
                    Filters.in(McpAuditInfo.HOST_COLLECTION_ID, mcpIds));
            List<McpAuditInfo> audits = Collections.emptyList();
            try {
                audits = McpAuditInfoDao.instance.findAll(auditFilter,
                        Projections.include(
                                McpAuditInfo.TYPE,
                                McpAuditInfo.RESOURCE_NAME,
                                McpAuditInfo.HOST_COLLECTION_ID,
                                McpAuditInfo.COMPONENT_RISK_ANALYSIS));
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(
                        "MCP risk: failed to load mcp_audit_info accountId=" + Context.accountId.get() + " : "
                                + e.getMessage(),
                        LogDb.DASHBOARD);
            }
            auditRowsLoaded = audits == null ? 0 : audits.size();
            if (audits != null) {
                for (McpAuditInfo a : audits) {
                    if (a.getResourceName() == null || a.getType() == null) {
                        continue;
                    }
                    ComponentRiskAnalysis cra = a.getComponentRiskAnalysis();
                    if (cra == null) {
                        auditRowsSkippedNoCra++;
                        continue;
                    }
                    String k = a.getHostCollectionId() + "\0" + a.getType() + '\0' + a.getResourceName();
                    ComponentRiskAnalysis existing = map.get(k);
                    if (existing == null) {
                        map.put(k, copyComponentRiskFlags(cra));
                    } else {
                        existing.setIsComponentMalicious(
                                existing.getIsComponentMalicious() || cra.getIsComponentMalicious());
                        existing.setHasPrivilegedAccess(
                                existing.getHasPrivilegedAccess() || cra.getHasPrivilegedAccess());
                    }
                }
            }
        }

        // Agent base risk score - independent of MCP tagging, any collection can carry one.
        Map<Integer, Double> agentBaseRiskScoreByCollectionId = new HashMap<>();
        try {
            Bson hasBaseRiskScore = Filters.exists(ApiCollection.BASE_RISK_SCORE, true);
            List<ApiCollection> agentScoredCols = ApiCollectionsDao.instance.findAll(hasBaseRiskScore,
                    Projections.include(ApiCollection.ID, ApiCollection.BASE_RISK_SCORE));
            if (agentScoredCols != null) {
                for (ApiCollection c : agentScoredCols) {
                    if (c.getBaseRiskScore() != null) {
                        agentBaseRiskScoreByCollectionId.put(c.getId(), c.getBaseRiskScore());
                    }
                }
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(
                    "MCP risk: failed to load agent baseRiskScore collections accountId=" + Context.accountId.get()
                            + " : " + e.getMessage(),
                    LogDb.DASHBOARD);
        }

        loggerMaker.debugAndAddToDb(
                "MCP risk context accountId=" + Context.accountId.get() + " mcpCollections="
                        + mcpIds.size() + " maliciousTaggedCollections=" + maliciousIds.size() + " auditRowsLoaded="
                        + auditRowsLoaded + " auditRiskKeys=" + map.size()
                        + " auditRowsWithoutComponentRisk=" + auditRowsSkippedNoCra
                        + " agentBaseRiskScoreCollections=" + agentBaseRiskScoreByCollectionId.size(),
                LogDb.DASHBOARD);
        return new McpRiskScoreContext(mcpIds, maliciousIds, map, agentBaseRiskScoreByCollectionId);
    }

    public static float getRiskScoreWithMcpAdjustments(ApiInfo apiInfo, float baseScore, McpRiskScoreContext ctx) {
        float score = baseScore;
        if (ctx == null || ctx.isEmpty() || apiInfo == null) {
            return score;
        }
        int cid = apiInfo.getId().getApiCollectionId();

        boolean malicious = false;
        boolean privileged = false;
        boolean maliciousServerTag = false;
        int nameCandidateCount = 0;

        if (ctx.mcpServerCollectionIds.contains(cid)) {
            List<String> nameCandidates = resolveMcpResourceNames(apiInfo);
            nameCandidateCount = nameCandidates.size();
            for (String name : nameCandidates) {
                if (name == null || name.isEmpty()) {
                    continue;
                }
                for (String t : MCP_AUDIT_TYPES) {
                    ComponentRiskAnalysis cra = ctx.componentRiskByTypeAndResource.get(cid + "\0" + t + '\0' + name);
                    if (cra == null) {
                        continue;
                    }
                    malicious |= cra.getIsComponentMalicious();
                    privileged |= cra.getHasPrivilegedAccess();
                }
            }
            if (malicious) {
                score += MCP_COMPONENT_MALICIOUS_SCORE_DELTA;
            }
            if (privileged) {
                score += MCP_COMPONENT_PRIVILEGE_SCORE_DELTA;
            }
            maliciousServerTag = ctx.maliciousMcpServerCollectionIds.contains(cid);
        }

        // Agent base risk score (0-2, -1 means "couldn't assess" and contributes nothing) - applies
        // to any collection that's been through AgentBaseRiskScoreCron, MCP-tagged or not.
        Double agentBaseRiskScore = ctx.agentBaseRiskScoreByCollectionId.get(cid);
        if (agentBaseRiskScore != null && agentBaseRiskScore > 0) {
            score += agentBaseRiskScore.floatValue();
        }

        score = Math.min(MCP_RISK_SCORE_CAP, score);
        if (maliciousServerTag) {
            score = Math.max(MCP_MALICIOUS_SERVER_COLLECTION_RISK_FLOOR, score);
        }
        if (Math.abs(score - baseScore) > 1e-4f) {
            loggerMaker.debugAndAddToDb(
                    "MCP risk adjusted accountId=" + Context.accountId.get() + " apiCollectionId="
                            + cid + " method=" + apiInfo.getId().getMethod() + " url="
                            + truncateForLog(apiInfo.getId().getUrl(), 160) + " baseScore=" + baseScore + " finalScore="
                            + score + " auditMalicious=" + malicious + " auditPrivileged=" + privileged
                            + " maliciousServerTag=" + maliciousServerTag + " agentBaseRiskScore=" + agentBaseRiskScore
                            + " nameCandidates=" + nameCandidateCount,
                    LogDb.DASHBOARD);
        }
        return score;
    }

    private static String truncateForLog(String s, int maxLen) {
        if (s == null) {
            return "";
        }
        if (s.length() <= maxLen) {
            return s;
        }
        return s.substring(0, maxLen) + "...";
    }

    private static boolean collectionHasMaliciousMcpServerTag(ApiCollection c) {
        List<CollectionTags> tags = c.getTagsList();
        if (tags == null) {
            return false;
        }
        for (CollectionTags t : tags) {
            if (Constants.AKTO_MALICIOUS_MCP_SERVER_TAG.equals(t.getKeyName())) {
                return true;
            }
        }
        return false;
    }

    private static ComponentRiskAnalysis copyComponentRiskFlags(ComponentRiskAnalysis c) {
        ComponentRiskAnalysis n = new ComponentRiskAnalysis();
        n.setIsComponentMalicious(c.getIsComponentMalicious());
        n.setHasPrivilegedAccess(c.getHasPrivilegedAccess());
        return n;
    }

    private static List<String> resolveMcpResourceNames(ApiInfo apiInfo) {
        List<String> parents = apiInfo.getParentMcpToolNames();
        if (parents != null && !parents.isEmpty()) {
            return parents;
        }
        String seg = urlLastPathSegment(apiInfo.getId().getUrl());
        if (seg.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.singletonList(seg);
    }

    private static String urlLastPathSegment(String url) {
        if (url == null) {
            return "";
        }
        int q = url.indexOf('?');
        if (q >= 0) {
            url = url.substring(0, q);
        }
        int slash = url.lastIndexOf('/');
        if (slash < 0 || slash >= url.length() - 1) {
            return "";
        }
        String raw = url.substring(slash + 1);
        try {
            return URLDecoder.decode(raw, StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            return raw;
        }
    }
}
