import React, { useState, useEffect } from "react";
import { Box, Spinner, VerticalStack, Text, HorizontalStack, Badge } from "@shopify/polaris";
import MarkdownViewer from "../../../components/shared/MarkdownViewer";
import { stripMarkdownLinks } from "../../../components/shared/markdownUtils";
import observeApi from "../api";
import PersistStore from "../../../../main/PersistStore";

export function buildSkillMarkdown(sampleMessage, skillName) {
    try {
        const parsed = JSON.parse(sampleMessage);
        const bodyStr = parsed?.request?.body || parsed?.requestPayload || "{}";
        const body = JSON.parse(bodyStr);
        if (!body.skill_name) return null;
        // Only return content for the specific skill being viewed
        if (skillName && body.skill_name.toLowerCase() !== skillName.toLowerCase()) return null;
        return (
            `# ${stripMarkdownLinks(body.skill_name)}\n\n` +
            (body.skill_description ? `**${stripMarkdownLinks(body.skill_description)}**\n\n` : "") +
            stripMarkdownLinks(body.skill_content || "")
        );
    } catch (_) {
        return null;
    }
}

// Skill invocation traffic can land on any of the skill's declaring collections, and the sample
// storage key's URL format (full hostname vs. path-only) varies by environment — so search every
// collection and try both URL forms before giving up. Shared by SkillComponentsView (asset opened
// directly) and SkillDetailPanel (skill drilled into from an Agent/MCP components list) so skill
// content shows up consistently everywhere in the new UI.
//
// Returns { markdown, collectionId } (collectionId is the one content was actually found in — a
// plugin-bundled skill lives directly in the plugin's own collection, so callers can look up that
// collection's plugin-name tag from it to show "bundled by plugin X").
export async function fetchSkillMarkdownFromCollections(collectionIds, skillName) {
    for (const collectionId of (collectionIds || [])) {
        const infoResp = await observeApi.fetchApiInfosForCollection(collectionId);
        const infos = infoResp?.apiInfoList || [];
        for (const info of infos) {
            const url = String(info?.id?.url || "");
            if (!url.toLowerCase().includes("/skills/")) continue;
            const method = info?.id?.method || "POST";
            // Try full URL (with hostname) then path-only — storage format varies by environment
            const pathOnly = url.replace(/^https?:\/\/[^/]+/, "");
            for (const candidateUrl of new Set([url, pathOnly])) {
                const resp = await observeApi.fetchSampleData(candidateUrl, collectionId, method);
                const samples = (resp?.sampleDataList || []).flatMap(s => s.samples || []);
                for (const sample of samples) {
                    const md = buildSkillMarkdown(sample, skillName);
                    if (md) return { markdown: md, collectionId };
                }
            }
        }
    }
    return { markdown: null, collectionId: null };
}

// Plugin-name tag off the collection a skill's content was actually found in (see
// fetchSkillMarkdownFromCollections above) — same collection-tag lookup ApiDetails.jsx uses.
export function getOwningPluginNameForCollection(collectionId) {
    if (!collectionId) return null;
    const allCollections = PersistStore.getState().allCollections;
    const collection = allCollections?.find(c => c.id === collectionId);
    const tag = collection?.envType?.find(t => t.keyName === "plugin-name");
    return tag?.value || null;
}

export default function SkillComponentsView({ asset, hideOwningPlugin, fetchMarkdown = fetchSkillMarkdownFromCollections, entityLabel = "skill" }) {
    const [markdown, setMarkdown] = useState(null);
    const [owningPluginName, setOwningPluginName] = useState(null);
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        const collectionIds = asset?.collectionIds;
        if (!collectionIds?.length) { setLoading(false); return; }
        let cancelled = false;

        (async () => {
            try {
                const { markdown: found, collectionId } = await fetchMarkdown(collectionIds, asset.name);
                if (!cancelled) {
                    setMarkdown(found || "");
                    setOwningPluginName(getOwningPluginNameForCollection(collectionId));
                }
            } catch {
                if (!cancelled) { setMarkdown(""); setOwningPluginName(null); }
            } finally {
                if (!cancelled) setLoading(false);
            }
        })();
        return () => { cancelled = true; };
    }, [asset?.id, asset?.collectionIds, asset?.name, fetchMarkdown]);

    if (loading) {
        return <Box padding="4"><Spinner accessibilityLabel="Loading" size="small" /></Box>;
    }

    if (!markdown) {
        return (
            <Box padding="8">
                <VerticalStack gap="1" inlineAlign="center">
                    <Text variant="bodySm" fontWeight="semibold">No content available</Text>
                    <Text variant="bodySm" color="subdued">No skill description found in captured traffic.</Text>
                </VerticalStack>
            </Box>
        );
    }

    return (
        <Box className="agentic-flex-fill">
            {!hideOwningPlugin && owningPluginName && (
                <Box paddingInlineStart="5" paddingInlineEnd="5" paddingBlockStart="5">
                    <HorizontalStack gap="1" blockAlign="center">
                        <Badge size="small" status="info">{owningPluginName}</Badge>
                        <Text variant="bodySm" color="subdued">{`uses this ${entityLabel}`}</Text>
                    </HorizontalStack>
                </Box>
            )}
            <Box
                paddingBlockStart="5"
                paddingBlockEnd="5"
                paddingInlineStart="5"
                paddingInlineEnd="5"
            >
                <MarkdownViewer markdown={markdown} />
            </Box>
        </Box>
    );
}
