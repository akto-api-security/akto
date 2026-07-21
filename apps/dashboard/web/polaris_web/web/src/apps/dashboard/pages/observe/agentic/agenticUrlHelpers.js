/**
 * Shared URL parsers for agentic / guardrail events.
 * Same logic as the Violations page (deriveSkillOrToolName).
 */

export function skillNameFromUrl(url) {
    if (!url) return null;
    const skillMatch = String(url).match(/\/skills\/([^/?#]+)/i);
    return skillMatch ? skillMatch[1] : null;
}

export function deriveSkillOrToolName(url) {
    if (!url) return null;
    const skill = skillNameFromUrl(url);
    if (skill) return skill;
    const toolMatch = String(url).match(/\/tools\/([^/?#]+)/i);
    if (toolMatch) return toolMatch[1];
    const mcpToolMatch = String(url).match(/\/mcp\/([^/?#]+)/i);
    if (mcpToolMatch) return mcpToolMatch[1];
    return null;
}
