import { useMemo } from "react";
import ReactFlow from "react-flow-renderer";
import { AgentNode, AgentEdge } from "../observe/api_collections/AgentDiscoverGraph";
import { isAgenticSecurityCategory } from "../../../main/labelHelper";

// ── Per-identity resource mappings ────────────────────────────────────────────
export const IDENTITY_RESOURCES = {
    "aws-cursor-key":      [{ label: "Bedrock",     type: "LLM",           category: "ai-model"  }, { label: "S3",          type: "AWS Resource",  category: "ai-tool"   }, { label: "EC2",         type: "AWS Resource",  category: "ai-tool"   }],
    "hr-slack-token":      [{ label: "Slack",       type: "Messaging",     category: "ai-tool"   }, { label: "HR System",   type: "Internal DB",   category: "internal"  }],
    "aws-env-sa":          [{ label: "S3",          type: "AWS Resource",  category: "ai-tool"   }, { label: "RDS",         type: "AWS Resource",  category: "ai-tool"   }],
    "github-oauth-456":    [{ label: "GitHub",      type: "Repository",    category: "internal"  }],
    "jira-token":          [{ label: "Jira",        type: "Issue Tracker", category: "ai-tool"   }, { label: "Confluence",  type: "Project",       category: "internal"  }],
    "internal-api-token":  [{ label: "Backend",     type: "Internal API",  category: "internal"  }],
    "airbnb-api-key":      [{ label: "Airbnb",      type: "External API",  category: "ai-tool"   }],
    "vscode-oauth":        [{ label: "GitHub",      type: "Repository",    category: "internal"  }, { label: "Azure",       type: "Cloud",         category: "ai-tool"   }],
    "docker-registry-key": [{ label: "Docker Hub",  type: "Registry",      category: "ai-tool"   }, { label: "ECR",         type: "AWS Resource",  category: "internal"  }],
    "github-actions-key":  [{ label: "GitHub",      type: "CI/CD",         category: "internal"  }, { label: "S3",          type: "AWS Resource",  category: "ai-tool"   }],
    "playwright-token":    [{ label: "Playwright",  type: "Browser API",   category: "ai-model"  }],
    "filesystem-token":    [{ label: "Local FS",    type: "Filesystem",    category: "internal"  }],
    "notion-token":        [{ label: "Notion",      type: "Workspace",     category: "ai-tool"   }],
    "huggingface-token":   [{ label: "HuggingFace", type: "LLM",           category: "ai-model"  }],
    "github-copilot-key":  [{ label: "Copilot",     type: "AI Coding",     category: "ai-model"  }, { label: "GitHub",      type: "Repository",    category: "internal"  }],
    "anthropic-api-key":   [{ label: "Claude",      type: "LLM",           category: "ai-model"  }],
};

export function getDefaultResources(identityName) {
    if (identityName.includes("aws"))    return [{ label: "S3",     type: "AWS Resource", category: "ai-tool"  }];
    if (identityName.includes("github")) return [{ label: "GitHub", type: "Repository",  category: "internal" }];
    if (identityName.includes("slack"))  return [{ label: "Slack",  type: "Messaging",   category: "ai-tool"  }];
    return [{ label: "API", type: "External API", category: "ai-tool" }];
}

const NODE_TYPES = { agentNode: AgentNode };
const EDGE_TYPES = { agentEdge: AgentEdge };

export default function IdentityGraph({ row }) {
    const { nodes, edges, height } = useMemo(() => {
        const isArgus = isAgenticSecurityCategory();
        const resources = IDENTITY_RESOURCES[row.identityName] || getDefaultResources(row.identityName);
        const n = resources.length;
        const SPACING = 82;
        const totalH = (n - 1) * SPACING;
        const centerY = Math.max(10, totalH / 2);
        const graphHeight = Math.max(150, totalH + 90);

        const makeNode = (id, x, y, component) => ({
            id,
            type: "agentNode",
            position: { x, y },
            draggable: false,
            data: { component: { id, ...component }, onNodeClick: null },
        });

        // Atlas: owner → agent → identity → resources
        // Argus:          agent → identity → resources  (no owner)
        const agentX    = isArgus ? 10  : 240;
        const identityX = isArgus ? 240 : 470;
        const resourceX = isArgus ? 470 : 700;

        const nodes = [
            ...(isArgus ? [] : [
                makeNode("owner", 10, centerY, {
                    label: row.owner || "Unknown",
                    type:  "Owner",
                    category: "internal",
                    description: `Owner of ${row.identityName}`,
                    status: "active",
                }),
            ]),
            makeNode("agent", agentX, centerY, {
                label: row.agent,
                type:  "AI Agent",
                category: "agent",
                description: `Agent using ${row.identityName}`,
                status: "active",
            }),
            makeNode("identity", identityX, centerY, {
                label: row.identityName,
                type:  row.type,
                category: "mcp",
                description: `${row.access} access via ${row.type}`,
                status: "active",
            }),
            ...resources.map((r, i) => makeNode(
                `res-${i}`,
                resourceX,
                centerY - totalH / 2 + i * SPACING,
                {
                    label: r.label,
                    type:  r.type,
                    category: r.category,
                    description: `${r.type}: ${r.label}`,
                    status: "connected",
                }
            )),
        ];

        const edges = [
            ...(isArgus ? [] : [
                { id: "e-o-a", source: "owner", target: "agent", type: "agentEdge", data: {} },
            ]),
            { id: "e-a-i", source: "agent",    target: "identity", type: "agentEdge", data: {} },
            ...resources.map((_, i) => ({
                id: `e-i-r${i}`,
                source: "identity",
                target: `res-${i}`,
                type: "agentEdge",
                data: { edgeParam: i === 0 ? row.access : undefined },
            })),
        ];

        return { nodes, edges, height: graphHeight };
    }, [row]);

    return (
        <div style={{ height, border: "1px solid #E4E5E7", borderRadius: 8, overflow: "hidden", background: "#F6F6F7" }}>
            <ReactFlow
                nodes={nodes}
                edges={edges}
                nodeTypes={NODE_TYPES}
                edgeTypes={EDGE_TYPES}
                nodesDraggable={false}
                nodesConnectable={false}
                elementsSelectable={false}
                panOnDrag={false}
                zoomOnScroll={false}
                fitView
                fitViewOptions={{ padding: 0.15 }}
            />
        </div>
    );
}
