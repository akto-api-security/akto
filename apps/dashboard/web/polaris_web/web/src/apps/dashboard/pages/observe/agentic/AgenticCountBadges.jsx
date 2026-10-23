import React from "react";
import { Badge, Tooltip } from "@shopify/polaris";
import { CLIENT_TYPES } from "./mcpClientHelper";

// Collection types that get a count badge, as [type, singular, plural]. Skill collections (e.g. the
// not-attached catch-all) get none: their skills are in the skills badge. Plugin collections have
// their own pluginCount badge.
const TYPE_BADGES = [
    [CLIENT_TYPES.AI_AGENT, "AI agent", "AI agents"],
    [CLIENT_TYPES.MCP_SERVER, "MCP server", "MCP servers"],
    [CLIENT_TYPES.LLM, "LLM", "LLMs"],
    [CLIENT_TYPES.SAAS_AGENT, "SaaS agent", "SaaS agents"],
];

const countText = (count, singular, plural) => `${count} ${count === 1 ? singular : plural}`;

/**
 * What a user/device holds, the same way on every agentic list: optionally the collection count
 * (grey), then one blue badge per entity — AI agents, MCP servers, LLMs, SaaS agents, skills,
 * plugins — shown only when non-zero. Risk tags (red/orange/yellow) stay separate from these.
 * An entity is a collection's hostname without its device prefix (<agent>.<name>): the same MCP
 * server under two agents counts twice, the same agent+server on two devices once — so a user's
 * counts are never below any one of their devices'. countsByType is keyed by CLIENT_TYPES values;
 * plugins are the Plugin-type entities. Skills are counted by name alone: agents that share a
 * skills folder (codex, codex-cli, codex-desktop) all report the same skills.
 */
export default function AgenticCountBadges({ collectionCount = 0, countsByType = {}, skillCount = 0, pluginCount = 0 }) {
    return (
        <>
            {collectionCount > 0 && (
                <Tooltip content={countText(collectionCount, "collection", "collections")} dismissOnMouseOut>
                    <Badge size="small" status="new">{collectionCount}</Badge>
                </Tooltip>
            )}
            {TYPE_BADGES.filter(([type]) => countsByType[type] > 0).map(([type, singular, plural]) => (
                <Badge key={type} size="small" status="info">{countText(countsByType[type], singular, plural)}</Badge>
            ))}
            {skillCount > 0 && <Badge size="small" status="info">{countText(skillCount, "skill", "skills")}</Badge>}
            {pluginCount > 0 && <Badge size="small" status="info">{countText(pluginCount, "plugin", "plugins")}</Badge>}
        </>
    );
}
