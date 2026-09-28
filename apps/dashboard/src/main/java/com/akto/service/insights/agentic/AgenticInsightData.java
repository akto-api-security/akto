package com.akto.service.insights.agentic;

import com.akto.dto.agentic_sessions.UserAnalysisData;
import com.akto.dto.insights.agentic.AgentFindingGroup;
import com.akto.dto.insights.agentic.DailyCount;
import com.akto.dto.testing.AgentConversationResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Every AGENTIC-only read InsightDataLoader performs, bundled the same way InsightDataBundle
 * bundles the ENDPOINT reads. Always non-null on InsightDataBundle (empty, loaded=false, for any
 * other CONTEXT_SOURCE) so providers never need a null check, only a `loaded` check.
 *
 * Every field here is a small, already-aggregated-in-Mongo/ES result (see the DAO/SearchClient
 * methods this is built from) — never a raw document list. Reuses existing DTOs rather than
 * inventing narrow one-off shapes: {@link AgentFindingGroup} backs three otherwise-independent
 * grouped reads (open issues, vulnerable-result counts, audit groups — see its own javadoc),
 * {@link AgentConversationResult} is the real conversation-result DTO (not a copy of a few of its
 * fields), and {@link UserAnalysisData} is the same per-service/device rollup ENDPOINT's own
 * token-totals read already returns. See service/posture/CLAUDE.md's "Argus (AGENTIC) insights"
 * section for the read table (index used, cap, pipeline shape) behind each one.
 */
public class AgenticInsightData {

    public final boolean loaded;
    public final AgentIndex agentIndex;
    public final List<AgentFindingGroup> openIssueGroups;
    public final List<AgentFindingGroup> vulnGroups;
    public final List<DailyCount> dailyCounts;
    public final Map<String, AgentConversationResult> validatedConversationsById; // conversationId -> row
    public final List<AgentFindingGroup> auditGroups;
    public final List<UserAnalysisData> serviceObservability; // per-serviceId (deviceId blank) rollup

    public AgenticInsightData(boolean loaded, AgentIndex agentIndex, List<AgentFindingGroup> openIssueGroups,
                               List<AgentFindingGroup> vulnGroups, List<DailyCount> dailyCounts,
                               Map<String, AgentConversationResult> validatedConversationsById,
                               List<AgentFindingGroup> auditGroups, List<UserAnalysisData> serviceObservability) {
        this.loaded = loaded;
        this.agentIndex = agentIndex != null ? agentIndex : new AgentIndex(Collections.emptyList());
        this.openIssueGroups = openIssueGroups != null ? openIssueGroups : Collections.emptyList();
        this.vulnGroups = vulnGroups != null ? vulnGroups : Collections.emptyList();
        this.dailyCounts = dailyCounts != null ? dailyCounts : Collections.emptyList();
        this.validatedConversationsById = validatedConversationsById != null ? validatedConversationsById : Collections.emptyMap();
        this.auditGroups = auditGroups != null ? auditGroups : Collections.emptyList();
        this.serviceObservability = serviceObservability != null ? serviceObservability : Collections.emptyList();
    }

    private static final AgenticInsightData EMPTY = new AgenticInsightData(
            false, new AgentIndex(Collections.emptyList()), new ArrayList<>(), new ArrayList<>(), new ArrayList<>(),
            new HashMap<>(), new ArrayList<>(), new ArrayList<>());

    /** For any bundle whose contextSource isn't AGENTIC — every ENDPOINT/GEN_AI/MCP/DAST provider
     *  reads this and finds it empty+not-loaded, never null. */
    public static AgenticInsightData empty() {
        return EMPTY;
    }
}
