import { Box, Card, HorizontalGrid, Text, VerticalStack } from '@shopify/polaris'
import { formatDuration } from './overviewApi'

export function KpiCard({ title, value, note, noteColor = 'subdued', onClick }) {
    const body = (
        <VerticalStack gap="1">
            <Text variant="bodyMd" color="subdued">{title}</Text>
            <Text variant="heading2xl" as="p">{value}</Text>
            <Text variant="bodySm" color={noteColor}>{note}</Text>
        </VerticalStack>
    )
    return (
        <Card padding="4">
            {onClick ? <Box className="cursor-pointer" onClick={onClick}>{body}</Box> : body}
        </Card>
    )
}

// Up is bad for violations, so a rise reads critical and a drop reads success.
function changeNote(changePct) {
    if (changePct === null || changePct === undefined) return { note: 'No previous period to compare', color: 'subdued' }
    if (changePct === 0) return { note: 'No change from previous period', color: 'subdued' }
    const word = changePct > 0 ? 'Up' : 'Down'
    return { note: `${word} ${Math.abs(changePct)}% from previous period`, color: changePct > 0 ? 'critical' : 'success' }
}

// Median time from detection to someone picking the case up.
function triageText(triage) {
    if (!triage) return { value: '-', note: 'Could not load' }
    if (!triage.triaged) return { value: '-', note: 'No cases triaged yet' }
    return { value: formatDuration(triage.medianSeconds), note: `${triage.triaged.toLocaleString()} case${triage.triaged === 1 ? '' : 's'} triaged` }
}

function OverviewKpiRow({ kpis, onOpenIssues, onOpenPolicies }) {
    const { violations, stopped, open, triage, ignored } = kpis
    const change = changeNote(violations.changePct)
    const triageInfo = triageText(triage)
    return (
        <HorizontalGrid columns={{ xs: 1, sm: 2, md: 3, lg: 5 }} gap="4">
            <KpiCard title="Violations" value={violations.value.toLocaleString()} note={change.note} noteColor={change.color} />
            <KpiCard
                title="Stopped before reaching users"
                value={stopped ? `${stopped.pct}%` : '-'}
                note={stopped ? `${stopped.blocked.toLocaleString()} blocked of ${stopped.all.toLocaleString()}` : 'Could not load'}
            />
            <KpiCard
                title="Open issues"
                value={open.value.toLocaleString()}
                note={`${open.critical.toLocaleString()} critical · ${open.unassigned.toLocaleString()} unassigned`}
                onClick={onOpenIssues}
            />
            <KpiCard title="Median time to triage" value={triageInfo.value} note={triageInfo.note} />
            <KpiCard
                title="Ignored violations"
                value={`${ignored.pct}%`}
                note={`${ignored.value.toLocaleString()} ignored`}
                onClick={onOpenPolicies}
            />
        </HorizontalGrid>
    )
}

export default OverviewKpiRow
