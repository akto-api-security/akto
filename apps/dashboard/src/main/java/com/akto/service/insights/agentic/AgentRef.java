package com.akto.service.insights.agentic;

import com.akto.dto.ApiCollection;
import com.akto.service.insights.InsightUtil;
import com.akto.util.AgenticObserveUtil;
import lombok.Getter;

/**
 * One agent/MCP server identity for the Argus (AGENTIC) posture surface — the apiCollectionId is
 * the join key every AGENTIC read (issues, guardrail activity, audit rows, observability) is
 * ultimately attributed back to. `name` (ApiCollection.name, not the hostName Atlas/ENDPOINT
 * parses) is what a Finding displays as "Agent" — see the CLAUDE.md decision this was built from.
 */
@Getter
public class AgentRef {
    private final int collectionId;
    private final String name;
    private final String type;        // AgenticObserveUtil.CLIENT_TYPE_*
    private final String environment; // InsightUtil.ENV_PRODUCTION | ENV_STAGING | ENV_DEVELOPMENT
    private final String vendor;      // nullable — InsightUtil.agenticVendorOf
    private final String hostName;
    private final boolean deactivated;
    private final boolean malicious;  // InsightUtil.isMaliciousMcpServer tag

    public AgentRef(ApiCollection c) {
        this.collectionId = c.getId();
        this.name = c.getName() != null ? c.getName() : c.getHostName();
        this.type = AgenticObserveUtil.getTypeFromCollection(c);
        this.environment = InsightUtil.environmentOf(c);
        this.vendor = InsightUtil.agenticVendorOf(c);
        this.hostName = c.getHostName();
        this.deactivated = c.isDeactivated();
        this.malicious = InsightUtil.isMaliciousMcpServer(c);
    }
}
