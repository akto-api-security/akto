package com.akto.dto.insights.agentic;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * One aggregated-in-Mongo group behind an Argus (AGENTIC) finding: a count of matching documents
 * for one {collection, type[, secondary]} key, plus a capped sample of some per-row value and the
 * most recent timestamp seen. The same shape backs three otherwise-independent reads that used to
 * each have their own near-identical DTO — see AgentFindingGroupAggregation, the shared pipeline
 * builder every one of them now calls:
 *
 * <ul>
 *   <li>Open red-team issues (TestingRunIssuesDao#openIssueGroupsForDashboard): type=testSubCategory,
 *       secondary=severity, sample=api urls, lastSeen=TestingRunIssues.lastSeen.</li>
 *   <li>Vulnerable-result counts (VulnerableTestingRunResultDao#redTeamAggregates): type=testSubType,
 *       secondary=null (severity isn't tracked at this level), sample=null (conversationIds need
 *       their own unwind-based facet — see that method), lastSeen=endTimestamp.</li>
 *   <li>Unapproved/malicious components (McpAuditInfoDao#auditGroupsForAgents): type=McpAuditInfo.type,
 *       secondary=remarks (a review status, not a true severity — reuses this slot rather than adding
 *       a fourth near-identical field), sample=resource names, lastSeen=lastDetected.</li>
 * </ul>
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class AgentFindingGroup {
    private int collectionId;
    private String type;
    private String secondary; // severity | remarks | null, depending on the source — see class javadoc
    private int count;
    private int lastSeen; // 0 when the source doesn't track one
    private List<String> sample; // capped, empty (never null) when the source has no sample field
}
