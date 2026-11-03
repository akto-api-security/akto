import { useEffect, useState } from "react";
import agenticObserveApi from "@/apps/dashboard/pages/observe/agentic/agenticObserveApi";
import { buildAgentBuiltinToolsFromStis, buildMcpComponentsFromStis } from "@/apps/dashboard/pages/observe/agentic/agenticPageBuilders";
import { CAPABILITY_LIST_LIMIT, CAPABILITY_TITLES } from "./constants";

const EMPTY = { tools: [], total: 0 };

// Most worrying first: malicious, privileged, then by risk and name.
const byConcern = (a, b) => (b.isMalicious - a.isMalicious) || (b.hasPrivilegedAccess - a.hasPrivilegedAccess) || (b.riskScore - a.riskScore) || a.name.localeCompare(b.name);

// An agent exposes its built-in tools; an MCP server exposes the tools Atlas has seen it serve.
const toolsOf = (rowType, bundle) => (rowType === "agent"
    ? buildAgentBuiltinToolsFromStis(bundle.stiEndpoints, bundle.apiInfoList, bundle.id, bundle.auditRows)
    : buildMcpComponentsFromStis(bundle.stiEndpoints, bundle.apiInfoList, bundle.id, bundle.auditRows).tools);

// The first CAPABILITY_LIST_LIMIT tools a discovered tool exposes, plus the total, from the STI bundles.
export default function useToolCapabilities(rowType, collectionIds) {
    const [capabilities, setCapabilities] = useState(EMPTY);
    const idsKey = (collectionIds || []).join(",");

    useEffect(() => {
        if (!CAPABILITY_TITLES[rowType] || !idsKey) {
            setCapabilities(EMPTY);
            return undefined;
        }
        let ignore = false;
        agenticObserveApi.fetchCollectionStiBundlesBatch(idsKey.split(","))
            .then((bundles) => {
                if (ignore) return;
                const byName = new Map();
                bundles.forEach((bundle) => toolsOf(rowType, bundle).forEach((tool) => { if (tool.name && !byName.has(tool.name)) byName.set(tool.name, tool); }));
                const tools = [...byName.values()].sort(byConcern);
                setCapabilities({ tools: tools.slice(0, CAPABILITY_LIST_LIMIT), total: tools.length });
            })
            .catch(() => { if (!ignore) setCapabilities(EMPTY); });
        return () => { ignore = true; };
    }, [rowType, idsKey]);

    return capabilities;
}
