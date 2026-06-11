import { useEffect, useMemo, useState } from "react";
import { HorizontalGrid, HorizontalStack, Text } from "@shopify/polaris";
import api from "./api";
import { enrichRow } from "./utils";
import { formatCompact } from "./constants";
import AgenticStatsCard from "../agentic/AgenticStatsCard";
import AgenticTopListCard from "../agentic/AgenticTopListCard";
import "../../../components/layouts/style.css";

// Distinct colors for the top-users segment bar (up to 8 users).
const USER_SEGMENT_COLORS = ["#9642FC", "#4285F4", "#10A37F", "#EAB308", "#F97316", "#DC2626", "#CC785C", "#06B6D4"];

// Summary band above the tabs, built from the same cards as the Agentic AI area so the
// look matches. Real data only: AI-interactions total + token sparkline, and Top models
// by tokens. Violation/severity panels from the design are omitted (no data source).
export default function SummaryBand({ currDateRange }) {
    const [sessions, setSessions] = useState([]);
    const [recent, setRecent] = useState([]);

    const epochs = useMemo(() => ({
        since: Math.floor(Date.parse(currDateRange.period.since) / 1000),
        until: Math.floor(Date.parse(currDateRange.period.until) / 1000),
    }), [currDateRange]);

    useEffect(() => {
        let cancelled = false;
        api.fetchSessions(epochs.since, epochs.until, {})
            .then(rows => { if (!cancelled) setSessions((rows || []).map(enrichRow)); });
        api.searchPrompts({ startTime: epochs.since, endTime: epochs.until, sortKey: "timestamp", sortOrder: 1, skip: 0, limit: 100 })
            .then(r => { if (!cancelled) setRecent(r?.value || []); });
        return () => { cancelled = true; };
    }, [epochs]);

    // Total tokens across all sessions (sessions are pre-enriched by enrichRow).
    const totalTokens = useMemo(
        () => sessions.reduce((s, r) => s + (Number(r._inputTokens) || 0) + (Number(r._outputTokens) || 0), 0),
        [sessions]
    );

    const topModelRows = useMemo(() => {
        const byModel = {};
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

    // Breakdown bar: top 8 users by tokens (sessions pre-enriched).
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
