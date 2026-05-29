import React, { useState, useEffect } from 'react'
import { Banner, Card, HorizontalGrid, HorizontalStack, VerticalStack, Text, Box, Divider, Button, TextField } from '@shopify/polaris'
import PatternSettingsPage from '../components/PatternSettingsPage'
import { ToggleComponent } from '../about/About'
import settingRequests from '../api'
import func from '@/util/func'
import DropdownSearch from '../../../components/shared/DropdownSearch'

const resourceName = { singular: 'pattern', plural: 'patterns' }

async function fetchProxyPatterns() {
    const response = await settingRequests.fetchAdminSettings()
    const settings = response?.accountSettings
    return { map: settings?.matchingPatternsForProxy, switchProxyMode: settings?.switchProxyMode || false }
}

const DOMAIN_KEYS = [
    { key: 'chattyDomains', label: 'Chatty Domains', description: 'Domains with high traffic volume' },
    { key: 'aiDomains', label: 'AI Domains', description: 'AI service domains detected by the agent' },
]

function DomainsCard() {
    const [domains, setDomains] = useState({ chattyDomains: [], aiDomains: [] })
    const [selected, setSelected] = useState({ chattyDomains: [], aiDomains: [] })
    const [inputValues, setInputValues] = useState({ chattyDomains: '', aiDomains: '' })
    const [saving, setSaving] = useState({ chattyDomains: false, aiDomains: false })

    useEffect(() => {
        settingRequests.fetchAdminSettings()
            .then(res => {
                const s = res?.accountSettings || {}
                setDomains({
                    chattyDomains: s.chattyDomains || [],
                    aiDomains: s.aiDomains || [],
                })
            })
            .catch(() => {})
    }, [])

    const applyResponse = (res) => {
        if (res?.chattyDomains !== undefined || res?.aiDomains !== undefined) {
            setDomains(prev => ({
                chattyDomains: res.chattyDomains ?? prev.chattyDomains,
                aiDomains: res.aiDomains ?? prev.aiDomains,
            }))
        }
    }

    const handleAdd = async (key) => {
        const val = inputValues[key]?.trim()
        if (!val) return
        setSaving(prev => ({ ...prev, [key]: true }))
        try {
            const res = await settingRequests.updateAccountDomains(key, [val], [])
            applyResponse(res)
            setInputValues(prev => ({ ...prev, [key]: '' }))
            func.setToast(true, false, 'Domain added')
        } catch {
            func.setToast(true, true, 'Failed to add domain')
        } finally {
            setSaving(prev => ({ ...prev, [key]: false }))
        }
    }

    const handleRemove = async (key) => {
        const toRemove = selected[key]
        if (!toRemove.length) return
        setSaving(prev => ({ ...prev, [key]: true }))
        try {
            const res = await settingRequests.updateAccountDomains(key, [], toRemove)
            applyResponse(res)
            setSelected(prev => ({ ...prev, [key]: [] }))
            func.setToast(true, false, `${toRemove.length} domain${toRemove.length > 1 ? 's' : ''} removed`)
        } catch {
            func.setToast(true, true, 'Failed to remove domains')
        } finally {
            setSaving(prev => ({ ...prev, [key]: false }))
        }
    }

    const toOptions = (list) => (list || []).map(d => ({ value: d, label: d }))

    return (
        <Card>
            <VerticalStack gap="5">
                <Text variant="headingMd" as="h2">Domains</Text>
                {DOMAIN_KEYS.map(({ key, label, description }, idx) => {
                    const list = domains[key]
                    const selCount = selected[key].length
                    return (
                        <Box key={key}>
                            {idx > 0 && <Box paddingBlockEnd="4"><Divider /></Box>}
                            <HorizontalGrid columns={2} gap="4">
                                <Box>
                                    <Text variant="bodyMd" as="p" fontWeight="semibold">{label}</Text>
                                    <Text variant="bodySm" tone="subdued">{description}</Text>
                                </Box>
                                <VerticalStack gap="2">
                                    <DropdownSearch
                                        id={`domain-search-${key}`}
                                        label=""
                                        placeholder={list.length ? `Search ${list.length} domains` : 'No domains yet'}
                                        optionsList={toOptions(list)}
                                        setSelected={(vals) => setSelected(prev => ({ ...prev, [key]: vals }))}
                                        preSelected={selected[key]}
                                        value={selCount > 0 ? `${selCount} domain${selCount > 1 ? 's' : ''} selected` : ''}
                                        itemName="domain"
                                        allowMultiple
                                    />
                                    {selCount > 0 && (
                                        <HorizontalStack align="end">
                                            <Button
                                                destructive
                                                size="slim"
                                                loading={saving[key]}
                                                onClick={() => handleRemove(key)}
                                            >
                                                {`Remove ${selCount} selected`}
                                            </Button>
                                        </HorizontalStack>
                                    )}
                                    <TextField
                                        value={inputValues[key]}
                                        onChange={(val) => setInputValues(prev => ({ ...prev, [key]: val }))}
                                        placeholder="Add domain e.g. openai.com"
                                        autoComplete="off"
                                        connectedRight={
                                            <Button
                                                primary
                                                loading={saving[key]}
                                                disabled={!inputValues[key]?.trim()}
                                                onClick={() => handleAdd(key)}
                                            >
                                                Add
                                            </Button>
                                        }
                                    />
                                </VerticalStack>
                            </HorizontalGrid>
                        </Box>
                    )
                })}
            </VerticalStack>
        </Card>
    )
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

    async function onDelete(value) {
        return await settingRequests.deleteProxyPattern(value, 'PROXY')
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
            onDelete={onDelete}
            patternKey="pattern"
            additionalCards={[<DomainsCard key="domains-card" />]}
        />
    )
}

export default ProxyPatterns
