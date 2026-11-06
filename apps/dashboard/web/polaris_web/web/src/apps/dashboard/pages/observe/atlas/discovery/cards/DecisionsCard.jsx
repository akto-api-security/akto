import { useEffect, useState } from "react";
import { Badge, Box, HorizontalStack, Pagination, Text, VerticalStack } from "@shopify/polaris";
import Pressable from "@/apps/dashboard/pages/dashboard/ask/components/Pressable";
import { getRiskLevel, getRiskStatus } from "@/apps/dashboard/pages/observe/agentic/agenticPageBuilders";
import ActionChip from "../../shared/ActionChip";
import discoveryApi from "../discoveryApi";
import DiscoveryCard from "../shared/DiscoveryCard";
import ToolAvatar from "../shared/ToolAvatar";
import { LOAD_STATE, SUBTITLES } from "../constants";

const PAGE_SIZE_SETTLE_MS = 300;

const riskLabel = (score) => {
    const level = getRiskLevel(score);
    return `${level.charAt(0)}${level.slice(1).toLowerCase()} risk`;
};

function DecisionRow({ row, pending, onOpen, onApprove, onBlock }) {
    return (
        <HorizontalStack align="space-between" blockAlign="center" gap="3" wrap={false}>
            <Pressable onClick={() => onOpen(row)} accessibilityLabel={`Open ${row.name}`} hoverBackground="bg-subdued" borderRadius="2" padding="1">
                <HorizontalStack gap="3" blockAlign="center" wrap={false}>
                    <ToolAvatar tool={row} />
                    <VerticalStack gap="0" inlineAlign="start">
                        <HorizontalStack gap="2" blockAlign="center" align="start" wrap={false}>
                            <Text as="span" fontWeight="semibold">{row.name}</Text>
                            {row.riskScore > 0 && <Badge size="small" status={getRiskStatus(row.riskScore)}>{riskLabel(row.riskScore)}</Badge>}
                            {row.isNew && <Badge size="small" status="info">New</Badge>}
                        </HorizontalStack>
                        <Text as="p" color="subdued">{row.why}</Text>
                    </VerticalStack>
                </HorizontalStack>
            </Pressable>
            <HorizontalStack gap="2" wrap={false}>
                <ActionChip size="slim" label="Block" disabled={pending} onClick={() => onBlock(row)} />
                <ActionChip size="slim" label="Approve" disabled={pending} onClick={() => onApprove(row)} />
            </HorizontalStack>
        </HorizontalStack>
    );
}

// Tools that still need a decision, a page at a time; pageSize is how many rows fit the card.
export default function DecisionsCard({ deviceScope, refreshKey, pageSize: measuredPageSize, pending, onOpen, onApprove, onBlock }) {
    const [pageSize, setPageSize] = useState(0);
    // A new scope or page size starts again from the first page, in the same render (no fetch at the old page).
    const filterKey = `${deviceScope}|${pageSize}`;
    const [pageState, setPageState] = useState({ filterKey, page: 0 });
    const page = pageState.filterKey === filterKey ? pageState.page : 0;
    const setPage = (next) => setPageState({ filterKey, page: next });
    // Forget the old filter's page, so returning to an earlier filter does not restore it.
    useEffect(() => { setPageState({ filterKey, page: 0 }); }, [filterKey]);
    const [data, setData] = useState(null);
    // The request (filters and page) that failed, so a new one never shows the old error.
    const requestKey = `${filterKey}|${page}`;
    const [failedKey, setFailedKey] = useState("");
    const failed = failedKey === requestKey;

    // The measured size (0 until known) settles as the neighbouring cards load; use it once it stops changing.
    useEffect(() => {
        if (!measuredPageSize) return undefined;
        const timer = setTimeout(() => setPageSize(measuredPageSize), PAGE_SIZE_SETTLE_MS);
        return () => clearTimeout(timer);
    }, [measuredPageSize]);

    useEffect(() => {
        // Fetch only once the size is known and has settled, so a changed scope costs one request.
        if (!pageSize || pageSize !== measuredPageSize) return undefined;
        let ignore = false;
        setFailedKey("");
        discoveryApi.fetchAtlasDecisions({ deviceScope, skip: page * pageSize, limit: pageSize })
            .then((response) => {
                if (ignore) return;
                // The last row of the last page was just decided: go back to the page that still has rows.
                if (page > 0 && (response?.rows || []).length === 0) setPage(Math.max(0, Math.ceil((response?.total || 0) / pageSize) - 1));
                else setData({ ...(response || { rows: [], total: 0 }), filterKey, page });
            })
            .catch(() => { if (!ignore) setFailedKey(requestKey); });
        return () => { ignore = true; };
    }, [deviceScope, refreshKey, page, pageSize, measuredPageSize]);

    // Rows fetched for another scope or size are not shown.
    const current = data?.filterKey === filterKey ? data : null;
    const rows = current?.rows || [];
    const total = current?.total || 0;
    // The pager follows the rows on screen, so it only moves once the next page has arrived.
    const shownPage = current ? current.page : page;
    const from = shownPage * pageSize + 1;
    // A failed page turn must not look like the previous page loaded fine.
    const pageFailed = failed && current?.page !== page;
    const status = current && !pageFailed ? LOAD_STATE.READY : failed ? LOAD_STATE.ERROR : LOAD_STATE.LOADING;
    return (
        <DiscoveryCard
            flush
            fill
            title="Needs a decision"
            subtitle={SUBTITLES.decisions}
            status={status}
            empty={rows.length === 0}
            emptyText="No risky or new tools need a decision."
        >
            {rows.map((row) => (
                <Box key={row.key} paddingBlockStart="2" paddingBlockEnd="2" paddingInlineStart="4" paddingInlineEnd="4" borderBlockStartWidth="1" borderColor="border-subdued">
                    <DecisionRow row={row} pending={pending} onOpen={onOpen} onApprove={onApprove} onBlock={onBlock} />
                </Box>
            ))}
            {total > pageSize && (
                <div className="discovery-card__footer">
                    <Box padding="2" borderBlockStartWidth="1" borderColor="border-subdued">
                        <HorizontalStack align="center">
                            <Pagination
                                label={`${from}-${Math.min(from + pageSize - 1, total)} of ${total}`}
                                hasPrevious={shownPage > 0}
                                onPrevious={() => setPage(shownPage - 1)}
                                hasNext={(shownPage + 1) * pageSize < total}
                                onNext={() => setPage(shownPage + 1)}
                            />
                        </HorizontalStack>
                    </Box>
                </div>
            )}
        </DiscoveryCard>
    );
}
