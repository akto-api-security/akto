import { useEffect, useMemo, useState } from "react";
import { Box, HorizontalGrid, Link, Text } from "@shopify/polaris";
import CardWithHeader from "@/apps/dashboard/pages/dashboard/new_components/CardWithHeader";
import SpinnerCentered from "@/apps/dashboard/components/progress/SpinnerCentered";
import { OverviewListRow, OverviewLoadError, OverviewShowMore } from "./OverviewCard";
import { formatCount } from "./atlasOverviewUtils";
import { ACTIVITY_TAB } from "@/apps/dashboard/pages/threat_detection/utils/threatDashboardUtils";

const NOISY_IGNORE_PCT = 40;
const COLUMNS = "minmax(0, 1.3fr) minmax(0, 1fr) 80px";
const COLUMNS_WITH_TUNE = `${COLUMNS} 90px`;
const PAGE_SIZE = 5;
const MISCONFIGURED_LABEL = "Misconfigured settings";

function buildPolicyRows(policies) {
    const groups = {};
    (policies || []).forEach((r) => {
        const name = r.misconfigured ? MISCONFIGURED_LABEL : (r.policy || "-");
        const group = groups[name] || (groups[name] = { name, misconfigured: r.misconfigured, skill: r.skill, count: 0, ignored: 0 });
        group.count += r.count;
        group.ignored += r.ignored;
    });
    return Object.values(groups)
        .sort((a, b) => b.count - a.count)
        .map((g) => ({ name: g.name, misconfigured: g.misconfigured, skill: g.skill, count: g.count, ignoredPct: Math.round((g.ignored * 100) / g.count) }));
}

function policyFilters(policy) {
    if (policy.misconfigured) return { eventStatus: ACTIVITY_TAB.MISCONFIGURED_SETTINGS };
    if (policy.skill) return { eventStatus: ACTIVITY_TAB.SKILLS_EVALUATIONS, latestAttack: policy.name };
    return { latestAttack: policy.name };
}

export default function PolicyViolationsCard({ data, onOpenActivity, onTune }) {
    const policies = useMemo(() => buildPolicyRows(data?.policies), [data]);
    const [visible, setVisible] = useState(PAGE_SIZE);
    useEffect(() => setVisible(PAGE_SIZE), [policies]);

    const shown = policies.slice(0, visible);
    const max = Math.max(1, ...shown.map((p) => p.count));
    const hasNoisy = shown.some((p) => p.ignoredPct >= NOISY_IGNORE_PCT);
    const columns = hasNoisy ? COLUMNS_WITH_TUNE : COLUMNS;
    const countWidth = `${Math.max(3, ...shown.map((p) => String(formatCount(p.count)).length))}ch`;
    return (
        <CardWithHeader title="Violations by policy" tooltipContent="High ignore rates usually mean a policy needs tuning" hasData>
            {data === null ? <SpinnerCentered height="200px" /> : data?.failed ? <OverviewLoadError /> : (
                <Box>
                    <OverviewListRow>
                        <HorizontalGrid columns={columns} gap="2">
                            <Text variant="bodySm" color="subdued">Policy</Text>
                            <Text variant="bodySm" color="subdued">Violations</Text>
                            <Text variant="bodySm" color="subdued">Ignored</Text>
                            {hasNoisy && <span />}
                        </HorizontalGrid>
                    </OverviewListRow>
                    {shown.map((policy) => {
                        const noisy = policy.ignoredPct >= NOISY_IGNORE_PCT;
                        return (
                            <OverviewListRow key={policy.name}>
                                <HorizontalGrid columns={columns} gap="2" alignItems="center">
                                    <Link monochrome removeUnderline onClick={() => onOpenActivity(policyFilters(policy))}>
                                        <Text variant="bodyMd" as="span" truncate>{policy.name}</Text>
                                    </Link>
                                    <HorizontalGrid columns={`1fr ${countWidth}`} gap="2" alignItems="center">
                                        <Box background="bg-inset" borderRadius="1" minHeight="6px">
                                            <Box background="bg-magic-strong" borderRadius="1" minHeight="6px" width={`${Math.round((policy.count / max) * 100)}%`} />
                                        </Box>
                                        <Text variant="bodyMd" alignment="end">{formatCount(policy.count)}</Text>
                                    </HorizontalGrid>
                                    <Text variant="bodyMd" color={noisy ? "warning" : "subdued"} fontWeight={noisy ? "semibold" : "regular"}>{`${policy.ignoredPct}%`}</Text>
                                    {hasNoisy && (noisy ? <Link onClick={() => onTune(policy)}>Tune policy</Link> : <span />)}
                                </HorizontalGrid>
                            </OverviewListRow>
                        );
                    })}
                    <OverviewShowMore total={policies.length} shown={shown.length} pageSize={PAGE_SIZE} onChange={setVisible} />
                </Box>
            )}
        </CardWithHeader>
    );
}
