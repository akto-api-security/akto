import { useMemo } from "react";
import { HorizontalGrid, HorizontalStack, Text } from "@shopify/polaris";
import { formatCompact, TOKEN_ESTIMATE_TOOLTIP } from "./constants";
import AgenticStatsCard from "../agentic/AgenticStatsCard";
import AgenticTopListCard from "../agentic/AgenticTopListCard";
import "../../../components/layouts/style.css";

const USER_SEGMENT_COLORS = ["#9642FC", "#4285F4", "#10A37F", "#EAB308", "#F97316", "#DC2626", "#CC785C", "#06B6D4"];

export default function SummaryBand({ sessions, recent }) {

    const totalTokens = useMemo(
        () => sessions.reduce((s, r) => s + (Number(r._inputTokens) || 0) + (Number(r._outputTokens) || 0), 0),
        [sessions]
    );

    const topModelRows = useMemo(() => {
        const byModel = {};
        // Sessions carry aggregated token counts per model (primary source).
        sessions.forEach(r => {
            const m = r._model;
            if (!m) return;
            byModel[m] = (byModel[m] || 0) + (Number(r._inputTokens) || 0) + (Number(r._outputTokens) || 0);
        });
        // Individual LLM spans supplement session data (e.g. when model isn't on the session aggregate).
        recent.forEach(r => {
            const m = r._model;
            if (!m) return;
            byModel[m] = (byModel[m] || 0) + (Number(r._inputTokens) || 0) + (Number(r._outputTokens) || 0);
        });
        return Object.entries(byModel)
            .sort((a, b) => b[1] - a[1])
            .slice(0, 5)
            .map(([model, tokens]) => ({
                id: model,
                name: model,
                type: "LLM",
                assetTagValue: model,
                renderValue: () => (
                    <HorizontalStack align="end" blockAlign="center" wrap={false}>
                        <Text variant="bodyMd" alignment="end">{formatCompact(tokens)}</Text>
                    </HorizontalStack>
                ),
            }));
    }, [recent]);

    const breakdown = useMemo(() => {
        const byUser = {};
        sessions.forEach(r => {
            const user = r.userName || "Unknown";
            byUser[user] = (byUser[user] || 0) + (Number(r._inputTokens) || 0) + (Number(r._outputTokens) || 0);
        });
        return Object.entries(byUser)
            .sort((a, b) => b[1] - a[1])
            .slice(0, 8)
            .map(([label, count], i) => ({ label, count, color: USER_SEGMENT_COLORS[i] }));
    }, [sessions]);

    const spark = useMemo(() => {
        if (!recent.length) return [0];
        const buckets = 12;
        const size = Math.max(1, Math.ceil(recent.length / buckets));
        const points = [];
        for (let i = 0; i < recent.length; i += size) {
            const slice = recent.slice(i, i + size);
            points.push(slice.reduce((s, r) => s + (Number(r._inputTokens) || 0) + (Number(r._outputTokens) || 0), 0));
        }
        return points.length ? points : [0];
    }, [recent]);

    const hasSpark = spark.some(v => v > 0);

    return (
        <HorizontalGrid columns={2} gap="4">
            <AgenticStatsCard
                title="Tokens used"
                titleTooltip={TOKEN_ESTIMATE_TOOLTIP}
                total={formatCompact(totalTokens)}
                sparklineCounts={hasSpark ? spark : undefined}
                sparklineColor="#9642FC"
                breakdown={breakdown}
            />
            <AgenticTopListCard
                title="Top models by tokens"
                columns={[{ label: "Model" }, { label: "Tokens" }]}
                rows={topModelRows}
                emptyStateText="No model data in this range"
            />
        </HorizontalGrid>
    );
}
