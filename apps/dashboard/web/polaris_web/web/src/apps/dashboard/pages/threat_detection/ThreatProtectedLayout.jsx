import { useEffect, useState } from 'react'
import { Outlet } from 'react-router-dom'
import { Box, Text, VerticalStack } from '@shopify/polaris'
import EmptyScreensLayout from '../../components/banners/EmptyScreensLayout'
import SpinnerCentered from '../../components/progress/SpinnerCentered'
import SessionStore from '../../../main/SessionStore'

function ThreatProtectedLayout({ probeUrl = '/api/fetchFiltersForThreatActors', featureName = 'Threat Detection' }) {
    const [accessState, setAccessState] = useState('loading') // 'loading' | 'granted' | 'denied'
    const [deniedMessage, setDeniedMessage] = useState('')

    useEffect(() => {
        const checkAccess = async () => {
            try {
                const resp = await fetch(probeUrl, {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                        'access-token': SessionStore.getState().accessToken || '',
                        ...(window.ACTIVE_ACCOUNT ? { 'account': String(window.ACTIVE_ACCOUNT) } : {})
                    },
                    body: JSON.stringify({})
                })
                if (resp.status === 403) {
                    let errorMsg = ''
                    try {
                        const data = await resp.json()
                        errorMsg = data?.actionErrors?.[0] || ''
                    } catch { /* ignore parse errors */ }
                    setDeniedMessage(errorMsg)
                    setAccessState('denied')
                } else {
                    setAccessState('granted')
                }
            } catch {
                setAccessState('granted')
            }
        }
        checkAccess()
    }, [])

    if (accessState === 'loading') {
        return <SpinnerCentered />
    }

    if (accessState === 'denied') {
        const isPlanDenied = deniedMessage.includes('not available in your plan')

        const descriptionText = isPlanDenied
            ? `${featureName} is not available in your current plan.`
            : (deniedMessage || `You do not have access to ${featureName} features.`)
        const secondaryText = isPlanDenied
            ? 'Please upgrade your plan or contact sales to get access.'
            : 'Please contact your administrator to get access.'

        return (
            <Box padding="8" width="100%">
                <EmptyScreensLayout
                    iconSrc={"/public/upgrade.svg"}
                    headingText={"Access Restricted"}
                    description={
                        <VerticalStack gap="2">
                            <Text variant="bodyMd" color="subdued" alignment="center">
                                {descriptionText}
                            </Text>
                            <Text variant="bodyMd" color="subdued" alignment="center">
                                {secondaryText}
                            </Text>
                        </VerticalStack>
                    }
                />
            </Box>
        )
    }

    return <Outlet />
}

export default ThreatProtectedLayout
