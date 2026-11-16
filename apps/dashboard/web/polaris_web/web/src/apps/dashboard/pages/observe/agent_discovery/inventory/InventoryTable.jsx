import { useMemo } from 'react'
import { Badge, Box, Button, HorizontalStack, ProgressBar, Tag, Text, VerticalStack } from '@shopify/polaris'
import func from '@/util/func'
import GithubSimpleTable from '../../../../components/tables/GithubSimpleTable'
import Dot from './Dot'
import { ago, createdOn, filterChips, matches, plural } from './inventoryUtils'

const HIGH_RISK_AT = 50
const RISK_BAR_WIDTH = '72px'

// textValue is what the CSV export writes where the cell is a component. The Source column filters on the platform.
const headers = [
    { title: 'Agent', text: 'Agent', value: 'nameComp', textValue: 'name' },
    { title: 'Source', text: 'Source', value: 'sourceComp', textValue: 'platform', filterKey: 'platform', showFilter: true },
    { title: 'Owning teams', text: 'Owning teams', value: 'teamsComp', textValue: 'teamsText' },
    { title: 'Tools', text: 'Tools', value: 'toolsComp', textValue: 'toolsText' },
    { title: 'Risk score', text: 'Risk score', value: 'riskComp', textValue: 'risk', sortActive: true },
    { title: 'Policies', text: 'Policies', value: 'policiesComp', textValue: 'policies' },
    { title: 'Created', text: 'Created', value: 'createdText' },
    { title: 'Last used', text: 'Last used', value: 'seenComp', textValue: 'seenText' },
]

// "asc" lists the largest first. columnIndex is the column's position, counting the leading one.
const sortOptions = [
    { label: 'Risk score', value: 'risk asc', directionLabel: 'Highest first', sortKey: 'risk', columnIndex: 5 },
    { label: 'Risk score', value: 'risk desc', directionLabel: 'Lowest first', sortKey: 'risk', columnIndex: 5 },
    { label: 'Last used', value: 'seenTs asc', directionLabel: 'Most recent', sortKey: 'seenTs', columnIndex: 8 },
    { label: 'Last used', value: 'seenTs desc', directionLabel: 'Least recent', sortKey: 'seenTs', columnIndex: 8 },
    { label: 'Created', value: 'createdTs asc', directionLabel: 'Newest', sortKey: 'createdTs', columnIndex: 7 },
    { label: 'Created', value: 'createdTs desc', directionLabel: 'Oldest', sortKey: 'createdTs', columnIndex: 7 },
]

const resourceName = { singular: 'agent', plural: 'agents' }

function rowOf(asset, generatedAt, colors) {
    const high = asset.risk >= HIGH_RISK_AT
    return {
        id: asset.id,
        name: asset.name,
        nameComp: (
            <VerticalStack gap="0">
                <Text as="span" fontWeight="semibold">{asset.name}</Text>
                {asset.reason && <Text as="span" variant="bodySm" color={high ? 'critical' : 'subdued'}>{asset.reason}</Text>}
            </VerticalStack>
        ),
        platform: asset.platform,
        type: asset.type,
        sourceComp: (
            <VerticalStack gap="0">
                <HorizontalStack gap="2" blockAlign="center">
                    <Dot color={colors[asset.platform]} />
                    <Text as="span">{asset.platform}</Text>
                </HorizontalStack>
                <Text as="span" variant="bodySm" color="subdued">{asset.type}</Text>
            </VerticalStack>
        ),
        teamsText: asset.teams.join(', ') || 'No team',
        teamsComp: asset.teams.length === 0
            ? <Badge status="critical">No team</Badge>
            : (
                <HorizontalStack gap="1" blockAlign="center" wrap={false}>
                    <Badge>{asset.teams[0]}</Badge>
                    {asset.teams.length > 1 && <Text as="span" color="subdued">{`+${asset.teams.length - 1}`}</Text>}
                </HorizontalStack>
            ),
        toolsText: `${asset.tools} · ${asset.privileged} privileged`,
        toolsComp: (
            <Text as="span">{asset.tools} <Text as="span" color="subdued">· {asset.privileged} privileged</Text></Text>
        ),
        // Searched as one piece of text: the tools by name.
        toolNamesText: asset.toolNames.join(' '),
        risk: asset.risk,
        riskComp: (
            <HorizontalStack gap="2" blockAlign="center" wrap={false}>
                <Text as="span" fontWeight="semibold" color={high ? 'critical' : undefined}>{asset.risk}</Text>
                <Box minWidth={RISK_BAR_WIDTH}><ProgressBar progress={asset.risk} size="small" color={high ? 'critical' : 'primary'} animated={false} /></Box>
            </HorizontalStack>
        ),
        policies: asset.policies,
        policiesComp: asset.policies ? <Text as="span">{asset.policies}</Text> : <Text as="span" fontWeight="semibold" color="critical">None</Text>,
        createdTs: asset.createdTs,
        createdText: createdOn(asset.createdTs, generatedAt),
        seenTs: asset.seenTs,
        seenText: ago(asset.seenTs, generatedAt),
        seenComp: <Text as="span" color={asset.flags.includes('dormant') ? 'warning' : undefined}>{ago(asset.seenTs, generatedAt)}</Text>,
    }
}

// The Export button on the page: every agent, whatever the table is showing.
export function exportAgents(data) {
    func.exportTableAsCSV(headers, data.assets.map((asset) => rowOf(asset, data.generatedAt, {})), 'agent-inventory')
}

// The agents that pass the filter, with the chips that say what is filtered and a way to remove each.
function InventoryTable({ data, colors, filter, onFilter, onOpen }) {
    const assets = useMemo(() => data.assets.filter((asset) => matches(asset, filter)), [data, filter])
    const rows = useMemo(() => assets.map((asset) => rowOf(asset, data.generatedAt, colors)), [assets, data.generatedAt, colors])
    const chips = filterChips(filter)

    const remove = (key) => {
        const rest = { ...filter }
        delete rest[key]
        if (key === 'flag') delete rest.flagLabel
        onFilter(rest)
    }

    return (
        <VerticalStack gap="3">
            <Text variant="headingMd" as="h2">All agents</Text>
            {chips.length > 0 && (
                <HorizontalStack gap="2" blockAlign="center">
                    <Text as="span" fontWeight="semibold">{`${plural(assets.length, 'agent')} match`}</Text>
                    {chips.map((chip) => <Tag key={chip.key} onRemove={() => remove(chip.key)}>{chip.label}</Tag>)}
                    <Button plain onClick={() => onFilter({})}>Clear all</Button>
                </HorizontalStack>
            )}
            {/* The key restarts the table's own page and search whenever the filter changes. */}
            <GithubSimpleTable
                key={JSON.stringify(filter)}
                resourceName={resourceName}
                useNewRow={true}
                condensedHeight={true}
                headers={headers}
                headings={headers}
                data={rows}
                sortOptions={sortOptions}
                searchKeys={['name', 'platform', 'type', 'teamsText', 'toolNamesText']}
                pageLimit={25}
                selectable={false}
                rowClickable={true}
                onRowClick={(row) => onOpen(row.id)}
                csvFileName="agent-inventory"
            />
        </VerticalStack>
    )
}

export default InventoryTable
