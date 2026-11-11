import { CircleTickMajor } from "@shopify/polaris-icons";
import { Badge, Box, HorizontalStack, Pagination, Text, VerticalStack } from "@shopify/polaris";
import Pressable from "@/apps/dashboard/pages/dashboard/ask/components/Pressable";
import observeFunc from "@/apps/dashboard/pages/observe/transform";
import ActionChip from "../../shared/ActionChip";
import DiscoveryCard from "../shared/DiscoveryCard";
import useDecisionsPage from "../shared/useDecisionsPage";
import ToolAvatar from "../shared/ToolAvatar";
import { SUBTITLES } from "../constants";
import { riskBadge } from "../transform";

function DecisionRow({ row, pending, onOpen, onApprove, onBlock }) {
    const risk = row.riskScore > 0 ? riskBadge(row.riskScore) : null;
    return (
        <HorizontalStack align="space-between" blockAlign="center" gap="3" wrap={false}>
            <Pressable onClick={() => onOpen(row)} accessibilityLabel={`Open ${row.name}`} hoverBackground="bg-subdued" borderRadius="2" padding="1">
                <HorizontalStack gap="3" blockAlign="center" wrap={false}>
                    <ToolAvatar tool={row} />
                    <VerticalStack gap="0" inlineAlign="start">
                        <HorizontalStack gap="2" blockAlign="center" align="start" wrap={false}>
                            <Text as="span" fontWeight="semibold">{row.name}</Text>
                            {risk && observeFunc.wrapRiskScoreTooltip(<Badge size="small" status={risk.status}>{risk.label}</Badge>, row.riskScore, row.baseRiskScore, row.riskReason)}
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
    const { rows, total, pageSize, shownPage, status, setPage } = useDecisionsPage({ deviceScope, refreshKey, measuredPageSize });
    const from = shownPage * pageSize + 1;
    return (
        <DiscoveryCard
            flush
            fill
            title="Needs a decision"
            subtitle={SUBTITLES.decisions}
            status={status}
            empty={rows.length === 0}
            emptyIcon={CircleTickMajor}
            emptyText="All caught up. No risky or new tools need a decision."
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
