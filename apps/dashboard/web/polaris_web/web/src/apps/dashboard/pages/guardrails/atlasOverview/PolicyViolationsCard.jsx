import { useEffect, useState } from "react";
import { Box, HorizontalGrid, Link, Text } from "@shopify/polaris";
import { OverviewListRow, OverviewShowMore } from "./OverviewCard";
import DiscoveryCard from "../../observe/atlas/discovery/shared/DiscoveryCard";
import { LOAD_STATE } from "../../observe/atlas/discovery/constants";
import observeFunc from "../../observe/transform";

const NOISY_IGNORE_PCT = 40;
const COLUMNS = "minmax(0, 1.3fr) minmax(0, 1fr) 80px 90px";

export default function PolicyViolationsCard({ policies, pageSize, loading, onPolicyClick, onTune }) {
    const [visible, setVisible] = useState(pageSize);
    useEffect(() => setVisible(pageSize), [policies, pageSize]);

    const shown = policies.slice(0, visible);
    const max = Math.max(1, ...shown.map((p) => p.count));
    const countWidth = `${Math.max(3, ...shown.map((p) => String(observeFunc.formatNumberWithCommas(p.count)).length))}ch`;
    return (
        <DiscoveryCard flush title="Violations by policy" subtitle="High ignore rates usually mean a policy needs tuning" status={loading ? LOAD_STATE.LOADING : LOAD_STATE.READY}>
            <OverviewListRow>
                <HorizontalGrid columns={COLUMNS} gap="2">
                    <Text variant="bodySm" color="subdued">Policy</Text>
                    <Text variant="bodySm" color="subdued">Violations</Text>
                    <Text variant="bodySm" color="subdued">Ignored</Text>
                    <span />
                </HorizontalGrid>
            </OverviewListRow>
            {shown.map((policy) => {
                const noisy = policy.ignoredPct >= NOISY_IGNORE_PCT;
                return (
                    <OverviewListRow key={policy.name}>
                        <HorizontalGrid columns={COLUMNS} gap="2" alignItems="center">
                            <Link monochrome removeUnderline onClick={() => onPolicyClick(policy)}>
                                <Text variant="bodyMd" as="span" truncate>{policy.name}</Text>
                            </Link>
                            <HorizontalGrid columns={`1fr ${countWidth}`} gap="2" alignItems="center">
                                <Box background="bg-inset" borderRadius="1" minHeight="6px">
                                    <Box background="bg-magic-strong" borderRadius="1" minHeight="6px" width={`${Math.round((policy.count / max) * 100)}%`} />
                                </Box>
                                <Text variant="bodyMd" alignment="end">{observeFunc.formatNumberWithCommas(policy.count)}</Text>
                            </HorizontalGrid>
                            <Text variant="bodyMd" color={noisy ? "warning" : "subdued"} fontWeight={noisy ? "semibold" : "regular"}>{`${policy.ignoredPct}%`}</Text>
                            {noisy ? <Link onClick={() => onTune(policy)}>Tune policy</Link> : <span />}
                        </HorizontalGrid>
                    </OverviewListRow>
                );
            })}
            <OverviewShowMore total={policies.length} shown={shown.length} pageSize={pageSize} onChange={setVisible} />
        </DiscoveryCard>
    );
}
