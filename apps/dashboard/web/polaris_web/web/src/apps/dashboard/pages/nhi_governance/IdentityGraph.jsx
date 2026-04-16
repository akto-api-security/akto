import { useMemo } from "react";
import { Box } from "@shopify/polaris";
import ReactFlow from "react-flow-renderer";
import { AgentNode, AgentEdge } from "../observe/api_collections/AgentDiscoverGraph";
import { isAgenticSecurityCategory } from "../../../main/labelHelper";
import { getAgentType } from "./nhiViolationsData";

// ── Per-identity resource mappings ────────────────────────────────────────────
export const IDENTITY_RESOURCES = {
    "copilot-api-key":    [{ label: "GitHub Copilot", type: "AI Coding",    category: "ai-model"  }],
    "slack-token":        [{ label: "Slack",           type: "Messaging",   category: "ai-tool"   }],
    "atlassian-api-key":  [{ label: "Jira",            type: "Issue Tracker", category: "ai-tool" }, { label: "Confluence",  type: "Project",      category: "internal"  }],
    "github-api-key":     [{ label: "GitHub",          type: "Repository",  category: "internal"  }],
    "notion-api-key":     [{ label: "Notion",          type: "Workspace",   category: "ai-tool"   }],
    "filesystem-token":   [{ label: "Local FS",        type: "Filesystem",  category: "internal"  }],
    "razorpay-token":     [{ label: "Razorpay",        type: "Payment API", category: "ai-tool"   }],
    "docker-token":       [{ label: "Docker Hub",      type: "Registry",    category: "ai-tool"   }, { label: "Containers",  type: "Docker",       category: "internal"  }],
    "playwright-token":   [{ label: "Playwright",      type: "Browser API", category: "ai-model"  }],
    "kite-api-key":       [{ label: "Kite",            type: "Trading API", category: "ai-tool"   }],
    "postgres-token":     [{ label: "PostgreSQL",   type: "Database",     category: "internal"  }],
    "notion-mcp-token":   [{ label: "Notion",       type: "Workspace",    category: "ai-tool"   }],
    "jetbrains-token":    [{ label: "JetBrains",    type: "IDE",          category: "internal"  }],
    "squareup-token":     [{ label: "Square",        type: "Payment API",  category: "ai-tool"   }],
    "alphavantage-key":   [{ label: "Alpha Vantage", type: "Finance API",  category: "ai-tool"   }],
    // Argus identities — gen-ai:LLM
    "openai-api-key":        [{ label: "OpenAI API",  type: "LLM",         category: "ai-model"  }],
    "cohere-api-key":        [{ label: "Cohere",      type: "LLM",         category: "ai-model"  }],
    "perplexity-api-key":    [{ label: "Perplexity",  type: "LLM",         category: "ai-model"  }],
    "langchain-api-key":     [{ label: "LangChain",   type: "LLM",         category: "ai-model"  }],
    // Argus identities — gen-ai:MCP Server
    "k9s-mcp-token":         [{ label: "K9s Trade",   type: "MCP Server",  category: "mcp"       }],
    "vulnerable-mcp-token":  [{ label: "Vuln MCP",    type: "MCP Server",  category: "mcp"       }],
    "akplatform-mcp-token":  [{ label: "AK Platform", type: "MCP Server",  category: "mcp"       }],
    // Argus identities — gen-ai:AI Agent
    "replicate-api-key":     [{ label: "Replicate",   type: "AI Agent",    category: "ai-tool"   }],
    "n8n-api-key":           [{ label: "N8N",         type: "AI Agent",    category: "ai-tool"   }],
    "jasper-api-key":        [{ label: "Jasper",      type: "AI Agent",    category: "ai-tool"   }],
    "luma-api-key":          [{ label: "Luma AI",     type: "AI Agent",    category: "ai-tool"   }],
    "chargebee-api-key":     [{ label: "Chargebee",   type: "AI Agent",    category: "ai-tool"   }],
    "copy-ai-token":         [{ label: "Copy.AI",     type: "AI Agent",    category: "ai-tool"   }],
    "babylon-api-key":       [{ label: "Babylon",     type: "AI Agent",    category: "ai-tool"   }],
    "jooksy-api-key":        [{ label: "Jooksy",      type: "AI Agent",    category: "ai-tool"   }],
    "anthropos-api-key":     [{ label: "Anthropos AI",type: "AI Agent",    category: "ai-tool"   }],
    "lttc-api-key":          [{ label: "LTTC AI",     type: "AI Agent",    category: "ai-tool"   }],
    "agentai-token":         [{ label: "AgentAI",     type: "AI Agent",    category: "ai-tool"   }],
};

export function getDefaultResources(identityName) {
    if (identityName.includes("github"))      return [{ label: "GitHub",      type: "Repository",  category: "internal" }];
    if (identityName.includes("slack"))       return [{ label: "Slack",       type: "Messaging",   category: "ai-tool"  }];
    if (identityName.includes("notion"))      return [{ label: "Notion",      type: "Workspace",   category: "ai-tool"  }];
    if (identityName.includes("docker"))      return [{ label: "Docker Hub",  type: "Registry",    category: "ai-tool"  }];
    if (identityName.includes("postgres"))    return [{ label: "PostgreSQL",  type: "Database",    category: "internal" }];
    if (identityName.includes("filesystem"))  return [{ label: "Local FS",    type: "Filesystem",  category: "internal" }];
    if (identityName.includes("aws"))         return [{ label: "S3",          type: "AWS Resource",category: "ai-tool"  }];
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
                type:  getAgentType(row.agent),
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
        <Box style={{ height, border: "1px solid #E4E5E7", borderRadius: 8, overflow: "hidden", background: "#F6F6F7" }}>
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
        </Box>
    );
}
