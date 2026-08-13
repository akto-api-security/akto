import React, { useState, useEffect } from "react";
import { Box, Spinner, VerticalStack, Text } from "@shopify/polaris";
import MarkdownViewer from "../../../components/shared/MarkdownViewer";
import { stripMarkdownLinks } from "../../../components/shared/markdownUtils";
import observeApi from "../api";

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
                    if (md) return md;
                }
            }
        }
    }
    return null;
}

export default function SkillComponentsView({ asset }) {
    const [markdown, setMarkdown] = useState(null);
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        const collectionIds = asset?.collectionIds;
        if (!collectionIds?.length) { setLoading(false); return; }
        let cancelled = false;

        (async () => {
            try {
                const found = await fetchSkillMarkdownFromCollections(collectionIds, asset.name);
                if (!cancelled) setMarkdown(found || "");
            } catch {
                if (!cancelled) setMarkdown("");
            } finally {
                if (!cancelled) setLoading(false);
            }
        })();
        return () => { cancelled = true; };
    }, [asset?.id, asset?.collectionIds, asset?.name]);

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
        <Box overflowY="scroll" className="agentic-flex-fill">
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
