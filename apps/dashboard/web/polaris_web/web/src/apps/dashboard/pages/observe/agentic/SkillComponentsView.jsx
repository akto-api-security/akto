import React, { useState, useEffect } from "react";
import { Box, Spinner, VerticalStack, Text } from "@shopify/polaris";
import MarkdownViewer from "../../../components/shared/MarkdownViewer";
import observeApi from "../api";

function buildSkillMarkdown(sampleMessage) {
    try {
        const parsed = JSON.parse(sampleMessage);
        const bodyStr = parsed?.request?.body || parsed?.requestPayload || "{}";
        const body = JSON.parse(bodyStr);
        if (!body.skill_name) return null;
        return (
            `# ${body.skill_name}\n\n` +
            (body.skill_description ? `**${body.skill_description}**\n\n` : "") +
            (body.skill_content || "")
        );
    } catch (_) {
        return null;
    }
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
                let found = null;
                for (const collectionId of collectionIds) {
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
                                const md = buildSkillMarkdown(sample);
                                if (md) { found = md; break; }
                            }
                            if (found) break;
                        }
                        if (found) break;
                    }
                    if (found) break;
                }
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
