import { useCallback, useMemo, useState } from 'react'
import { Badge, Box, Button, HorizontalStack, IndexFiltersMode, ProgressBar, Tag, Text, VerticalStack } from '@shopify/polaris'
import func from '@/util/func'
import GithubServerTable from '../../../../components/tables/GithubServerTable'
import Dot from './Dot'
import { fetchAgents } from './inventoryApi'
import { TABLE_STATE_KEY, ago, createdOn, filterChips, plural } from './inventoryUtils'

const HIGH_RISK_AT = 50
const RISK_BAR_WIDTH = '72px'
const PAGE_LIMIT = 25
const EXPORT_PAGE = 500

// textValue is what the CSV export writes where the cell is a component.
const headers = [
    { title: 'Asset', text: 'Asset', value: 'nameComp', textValue: 'name' },
    { title: 'Source', text: 'Source', value: 'sourceComp', textValue: 'platform', filterKey: 'platform' },
    { title: 'Owning teams', text: 'Owning teams', value: 'teamsComp', textValue: 'teamsText' },
    { title: 'Tools', text: 'Tools', value: 'toolsComp', textValue: 'toolsText' },
    { title: 'Risk score', text: 'Risk score', value: 'riskComp', textValue: 'risk', sortActive: true },
    { title: 'Policies', text: 'Policies', value: 'policiesComp', textValue: 'policies' },
    { title: 'Created', text: 'Created', value: 'createdText' },
    { title: 'Last used', text: 'Last used', value: 'seenComp', textValue: 'seenText' },
]

// columnIndex is the column's position, counting the leading one.
const sortOptions = [
    { label: 'Risk score', value: 'risk desc', directionLabel: 'Highest first', sortKey: 'risk', columnIndex: 5 },
    { label: 'Risk score', value: 'risk asc', directionLabel: 'Lowest first', sortKey: 'risk', columnIndex: 5 },
    { label: 'Last used', value: 'seenTs desc', directionLabel: 'Most recent', sortKey: 'seenTs', columnIndex: 8 },
    { label: 'Last used', value: 'seenTs asc', directionLabel: 'Least recent', sortKey: 'seenTs', columnIndex: 8 },
    { label: 'Created', value: 'createdTs desc', directionLabel: 'Newest', sortKey: 'createdTs', columnIndex: 7 },
    { label: 'Created', value: 'createdTs asc', directionLabel: 'Oldest', sortKey: 'createdTs', columnIndex: 7 },
]

const resourceName = { singular: 'asset', plural: 'assets' }

function rowOf(agent, generatedAt, colors) {
    const high = agent.risk >= HIGH_RISK_AT
    return {
        id: agent.id,
        name: agent.name,
        nameComp: (
            <VerticalStack gap="0">
                <Text as="span" fontWeight="semibold">{agent.name}</Text>
                {agent.reason && <Text as="span" variant="bodySm" color={high ? 'critical' : 'subdued'}>{agent.reason}</Text>}
            </VerticalStack>
        ),
        platform: agent.platform,
        sourceComp: (
            <VerticalStack gap="0">
                <HorizontalStack gap="2" blockAlign="center">
                    <Dot color={colors[agent.platform]} />
                    <Text as="span">{agent.platform}</Text>
                </HorizontalStack>
                <Text as="span" variant="bodySm" color="subdued">{agent.type}</Text>
            </VerticalStack>
        ),
        teamsText: agent.teams.join(', ') || 'No team',
        teamsComp: agent.teams.length === 0
            ? <Badge status="critical">No team</Badge>
            : (
                <HorizontalStack gap="1" blockAlign="center" wrap={false}>
                    <Badge>{agent.teams[0]}</Badge>
                    {agent.teams.length > 1 && <Text as="span" color="subdued">{`+${agent.teams.length - 1}`}</Text>}
                </HorizontalStack>
            ),
        toolsText: `${agent.tools} · ${agent.privileged} privileged`,
        toolsComp: (
            <Text as="span">{agent.tools} <Text as="span" color="subdued">· {agent.privileged} privileged</Text></Text>
        ),
        risk: agent.risk,
        riskComp: (
            <HorizontalStack gap="2" blockAlign="center" wrap={false}>
                <Text as="span" fontWeight="semibold" color={high ? 'critical' : undefined}>{agent.risk}</Text>
                <Box minWidth={RISK_BAR_WIDTH}><ProgressBar progress={agent.risk} size="small" color={high ? 'critical' : 'primary'} animated={false} /></Box>
            </HorizontalStack>
        ),
        policies: agent.policies,
        policiesComp: agent.policies ? <Text as="span">{agent.policies}</Text> : <Text as="span" fontWeight="semibold" color="critical">None</Text>,
        createdText: createdOn(agent.createdTs, generatedAt),
        seenText: ago(agent.seenTs, generatedAt),
        seenComp: <Text as="span" color={agent.dormant ? 'warning' : undefined}>{ago(agent.seenTs, generatedAt)}</Text>,
    }
}

// The Export button on the page: every agent, whatever the table is showing.
export async function exportAgents() {
    try {
        const rows = []
        let total = 0
        do {
            const page = await fetchAgents({ skip: rows.length, limit: EXPORT_PAGE, sortKey: 'risk', sortOrder: -1 })
            total = page.total
            if (page.agents.length === 0) break
            rows.push(...page.agents.map((agent) => rowOf(agent, page.generatedAt, {})))
        } while (rows.length < total)
        func.exportTableAsCSV(headers, rows, 'agent-inventory')
    } catch (error) {
        func.setToast(true, true, 'Failed to export the assets')
    }
}

// The assets (agents and MCP servers) that pass the filter, paged by the backend, with chips that say what is filtered and a way to remove each.
function InventoryTable({ platforms, posture, colors, filter, onFilter, onOpen }) {
    const [matchCount, setMatchCount] = useState(null)
    const chips = filterChips(filter)
    // Source and Posture are the table's own filters, like the ones on the other tables.
    const filters = useMemo(() => [{
        key: 'platform', label: 'Source', title: 'Source', type: 'select', multiple: true,
        choices: platforms.map((platform) => ({ label: platform.name, value: platform.name })),
    }, {
        key: 'posture', label: 'Posture', title: 'Posture', type: 'select', singleSelect: true,
        choices: posture.map((group) => ({ label: `${group.label} · ${group.count}`, value: group.key })),
    }], [platforms, posture])
    const postureLabels = useMemo(() => Object.fromEntries(posture.map((group) => [group.key, group.label])), [posture])
    const disambiguateLabel = useCallback((key, value) => (key === 'posture' ? postureLabels[value[0]] || value[0] : func.convertToDisambiguateLabelObj(value, null, 2)), [postureLabels])

    const fetchData = useCallback(async (sortKey, sortOrder, skip, limit, tableFilters, filterOperators, queryValue) => {
        const scope = { ...filter }
        delete scope.flagLabel
        try {
            // The table sends -1 for "asc" and 1 for "desc", the reverse of Mongo.
            const page = await fetchAgents({ ...scope, platforms: tableFilters?.platform || [], posture: tableFilters?.posture?.[0], queryValue, sortKey, sortOrder: sortOrder === -1 ? 1 : -1, skip, limit })
            setMatchCount(page.total)
            return { value: page.agents.map((agent) => rowOf(agent, page.generatedAt, colors)), total: page.total }
        } catch (error) {
            func.setToast(true, true, 'Failed to load the assets')
            return { value: [], total: 0 }
        }
    }, [filter, colors])

    const remove = (key) => {
        const rest = { ...filter }
        delete rest[key]
        if (key === 'flag') delete rest.flagLabel
        onFilter(rest)
    }

    return (
        <VerticalStack gap="3">
            <Text variant="headingMd" as="h2">All assets</Text>
            {Object.keys(filter).length > 0 && (
                <HorizontalStack gap="2" blockAlign="center">
                    {matchCount !== null && <Text as="span" fontWeight="semibold">{`${plural(matchCount, 'asset')} match`}</Text>}
                    {chips.map((chip) => <Tag key={chip.key} onRemove={() => remove(chip.key)}>{chip.label}</Tag>)}
                    <Button plain onClick={() => onFilter({})}>Clear all</Button>
                </HorizontalStack>
            )}
            {/* The key restarts the table's own page and search whenever the filter changes. */}
            <GithubServerTable
                key={JSON.stringify(filter)}
                fetchData={fetchData}
                resourceName={resourceName}
                useNewRow={true}
                condensedHeight={true}
                headers={headers}
                headings={headers}
                filters={filters}
                filterStateUrl={TABLE_STATE_KEY}
                mode={IndexFiltersMode.Default}
                disambiguateLabel={disambiguateLabel}
                sortOptions={sortOptions}
                pageLimit={PAGE_LIMIT}
                selectable={false}
                rowClickable={true}
                onRowClick={(row) => onOpen(row.id)}
                csvFileName="agent-inventory"
            />
        </VerticalStack>
    )
}

export default InventoryTable
