import { useCallback, useEffect, useMemo, useState } from 'react'
import { useLocation, useNavigate, useSearchParams } from 'react-router-dom'
import { Banner, Box, Tabs, VerticalStack } from '@shopify/polaris'
import PersistStore from '../../../../../main/PersistStore'
import PageWithMultipleCards from '../../../../components/layouts/PageWithMultipleCards'
import SpinnerCentered from '../../../../components/progress/SpinnerCentered'
import TitleWithInfo from '../../../../components/shared/TitleWithInfo'
import InventorySummary from './InventorySummary'
import InventoryAnalytics from './InventoryAnalytics'
import InventoryTable from './InventoryTable'
import { fetchInventory } from './inventoryApi'
import { POSTURE_FLAGS, TABLE_STATE_KEY, inventoryAgentPath, paramsOf, platformColors, plural, viewOf } from './inventoryUtils'

const TAB_SUMMARY = 0
const TAB_ANALYTICS = 1
const TAB_ASSETS = 2
const IDENTITIES_PATH = '/dashboard/nhi/identities'
const NAMED_PLATFORMS = 3

function AgentInventory() {
    const navigate = useNavigate()
    const location = useLocation()
    const [searchParams, setSearchParams] = useSearchParams()
    const [data, setData] = useState(null)
    const [loading, setLoading] = useState(true)
    const [reloadKey, setReloadKey] = useState(0)
    // The tab and filter are read from the URL; the table's own params in it are left alone.
    const { tab, filter: urlFilter } = viewOf(searchParams)
    const filter = useMemo(() => urlFilter, [JSON.stringify(urlFilter)]) // eslint-disable-line react-hooks/exhaustive-deps
    // Arriving from a tab or chart resets the filters the table remembers; a posture group becomes the table's Posture filter, any other flag a chip.
    const show = useCallback((nextTab, nextFilter = {}) => {
        const posture = POSTURE_FLAGS.includes(nextFilter.flag) ? nextFilter.flag : ''
        const { flag, flagLabel, ...rest } = nextFilter
        const { filtersMap, setFiltersMap } = PersistStore.getState()
        const remembered = posture ? [{ key: 'posture', value: { values: [posture], negated: false } }] : []
        setFiltersMap({ ...filtersMap, [TABLE_STATE_KEY]: { filters: remembered, sort: filtersMap[TABLE_STATE_KEY]?.sort || [] } })
        setSearchParams(paramsOf(nextTab, posture ? rest : nextFilter))
    }, [setSearchParams])
    // Chips removed inside the table keep the table's own Source filter.
    const editFilter = useCallback((nextFilter) => setSearchParams(paramsOf(TAB_ASSETS, nextFilter)), [setSearchParams])

    useEffect(() => {
        let cancelled = false
        fetchInventory()
            .then((inventory) => { if (!cancelled) setData(inventory) })
            .catch(() => { if (!cancelled) setData(null) })
            .finally(() => { if (!cancelled) setLoading(false) })
        return () => { cancelled = true }
    }, [reloadKey])

    // Every number and chart on the other tabs opens the assets behind it.
    const showAssets = useCallback((next) => show(TAB_ASSETS, next), [show])
    // The asset's back arrow returns to this exact view.
    const openAsset = useCallback((id) => navigate(inventoryAgentPath(id), { state: { backUrl: location.pathname + location.search } }), [navigate, location.pathname, location.search])
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
            { id: 'assets', content: `All assets · ${data.counts.total}` },
        ]
        body = (
            <VerticalStack gap="4">
                {/* Moving between tabs by hand clears the filter; opening the assets from a chart sets it. */}
                <Tabs tabs={tabs} selected={tab} onSelect={(index) => show(index)} />
                {tab === TAB_SUMMARY && <InventorySummary data={data} colors={colors} onFilter={showAssets} onOpen={openAsset} onIdentities={openIdentities} onRevoked={reload} onAnalytics={() => show(TAB_ANALYTICS)} />}
                {tab === TAB_ANALYTICS && <InventoryAnalytics data={data} colors={colors} onFilter={showAssets} onOpen={openAsset} />}
                {tab === TAB_ASSETS && <InventoryTable platforms={data.platforms} posture={data.posture} colors={colors} filter={filter} onFilter={editFilter} onOpen={openAsset} />}
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
                <TitleWithInfo
                    titleText="Agent inventory"
                    tooltipContent="Every agent and MCP server Argus found in your traffic, with its risk, tools and last activity."
                />
            }
            subtitle={subtitle}
            isFirstPage
            components={[<Box key="body">{body}</Box>]}
        />
    )
}

export default AgentInventory
