import React, { useState } from 'react'
import { Banner } from '@shopify/polaris'
import PatternSettingsPage from '../components/PatternSettingsPage'
import { ToggleComponent } from '../about/About'
import settingRequests from '../api'
import func from '@/util/func'

const resourceName = { singular: 'pattern', plural: 'patterns' }

async function fetchProxyPatterns() {
    const response = await settingRequests.fetchAdminSettings()
    const settings = response?.accountSettings
    return { map: settings?.matchingPatternsForProxy, switchProxyMode: settings?.switchProxyMode || false }
}

function ProxyPatterns() {
    const [switchProxyMode, setSwitchProxyMode] = useState(false)

    async function onFetch() {
        const { map, switchProxyMode: mode } = await fetchProxyPatterns()
        setSwitchProxyMode(mode)
        return map
    }

    async function handleToggle(value) {
        setSwitchProxyMode(value)
        try {
            await settingRequests.addMatchingPatternForProxy('', value)
            func.setToast(true, false, `Proxy mode ${value ? 'enabled' : 'disabled'}`)
        } catch (e) {
            setSwitchProxyMode(!value)
            func.setToast(true, true, 'Failed to update proxy mode')
        }
    }

    async function onAdd(value) {
        return await settingRequests.addMatchingPatternForProxy(value, switchProxyMode)
    }

    const extraContent = (
        <>
            <ToggleComponent text="Proxy Mode" onToggle={handleToggle} initial={switchProxyMode} />
            {!switchProxyMode && <Banner status="warning">Enable proxy mode to add patterns.</Banner>}
        </>
    )

    return (
        <PatternSettingsPage
            title="Proxy Patterns"
            cardTitle="Add Proxy Pattern"
            description="Add patterns to match proxy traffic. These are used to identify requests routed through a proxy."
            extraContent={extraContent}
            inputLabel="Pattern"
            placeholder="e.g. .internal.example.com."
            inputDisabled={!switchProxyMode}
            tableKey="proxy-patterns-table"
            resourceName={resourceName}
            onFetch={onFetch}
            onAdd={onAdd}
            patternKey="pattern"
        />
    )
}

export default ProxyPatterns
