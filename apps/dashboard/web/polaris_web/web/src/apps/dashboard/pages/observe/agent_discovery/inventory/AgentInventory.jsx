import { useCallback, useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Badge, Banner, Box, Button, HorizontalStack, Tabs, VerticalStack } from '@shopify/polaris'
import PageWithMultipleCards from '../../../../components/layouts/PageWithMultipleCards'
import SpinnerCentered from '../../../../components/progress/SpinnerCentered'
import TitleWithInfo from '../../../../components/shared/TitleWithInfo'
import InventorySummary from './InventorySummary'
import InventoryAnalytics from './InventoryAnalytics'
import InventoryTable, { exportAgents } from './InventoryTable'
import { fetchInventory } from './inventoryApi'
import { agentPath, platformColors, plural } from './inventoryUtils'

const TAB_SUMMARY = 0
const TAB_ANALYTICS = 1
const TAB_AGENTS = 2
const IDENTITIES_PATH = '/dashboard/nhi/identities'
const NAMED_PLATFORMS = 3

function AgentInventory() {
    const navigate = useNavigate()
    const [data, setData] = useState(null)
    const [loading, setLoading] = useState(true)
    const [tab, setTab] = useState(TAB_SUMMARY)
    const [filter, setFilter] = useState({})
    const [reloadKey, setReloadKey] = useState(0)

    useEffect(() => {
        let cancelled = false
        fetchInventory()
            .then((inventory) => { if (!cancelled) setData(inventory) })
            .catch(() => { if (!cancelled) setData(null) })
            .finally(() => { if (!cancelled) setLoading(false) })
        return () => { cancelled = true }
    }, [reloadKey])

    // Every number and chart on the other tabs opens the assets behind it.
    const showAssets = useCallback((next) => {
        setFilter(next)
        setTab(TAB_AGENTS)
    }, [])
    const openAsset = useCallback((id) => navigate(agentPath(id)), [navigate])
    const openIdentities = useCallback(() => navigate(IDENTITIES_PATH), [navigate])
    const reload = useCallback(() => setReloadKey((key) => key + 1), [])
    const colors = useMemo(() => (data ? platformColors(data.platforms) : {}), [data])

    let body
    if (loading) {
        body = <SpinnerCentered />
    } else if (!data) {
        body = <Banner status="critical" title="Couldn't load the inventory">Try again in a moment.</Banner>
    } else {
        const tabs = [
            { id: 'summary', content: 'Summary' },
            { id: 'analytics', content: 'Analytics' },
            { id: 'agents', content: `All agents · ${data.counts.total}` },
        ]
        body = (
            <VerticalStack gap="4">
                {/* Moving between tabs by hand clears the filter; opening the assets from a chart sets it. */}
                <Tabs tabs={tabs} selected={tab} onSelect={(index) => { setFilter({}); setTab(index) }} />
                {tab === TAB_SUMMARY && <InventorySummary data={data} colors={colors} onFilter={showAssets} onOpen={openAsset} onIdentities={openIdentities} onRevoked={reload} onAnalytics={() => { setFilter({}); setTab(TAB_ANALYTICS) }} />}
                {tab === TAB_ANALYTICS && <InventoryAnalytics data={data} colors={colors} onFilter={showAssets} onOpen={openAsset} />}
                {tab === TAB_AGENTS && <InventoryTable platforms={data.platforms} colors={colors} filter={filter} onFilter={setFilter} onOpen={openAsset} />}
            </VerticalStack>
        )
    }

    // "Kubernetes, n8n, AWS Bedrock and others": the biggest named platforms, then the rest.
    const named = data ? data.platforms.filter((p) => p.name !== 'Other').slice(0, NAMED_PLATFORMS).map((p) => p.name) : []
    const subtitle = data
        ? `${plural(data.counts.total, 'agent')} across ${named.join(', ')}${data.platforms.length > named.length ? ' and others' : ''}. ${data.counts.high} have a risk score of 50 or more.`
        : undefined

    return (
        <PageWithMultipleCards
            title={
                <HorizontalStack gap="2" blockAlign="center">
                    <TitleWithInfo
                        titleText="Agent inventory"
                        tooltipContent="Every agent and MCP server Argus found in your traffic, with its risk, tools and last activity."
                    />
                    <Badge status="info">Beta</Badge>
                </HorizontalStack>
            }
            subtitle={subtitle}
            isFirstPage
            secondaryActions={data && <Button onClick={exportAgents}>Export</Button>}
            components={[<Box key="body">{body}</Box>]}
        />
    )
}

export default AgentInventory
