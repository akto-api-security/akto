import React from 'react'
import PageWithMultipleCards from '../../components/layouts/PageWithMultipleCards'
import { Text } from '@shopify/polaris'
import { getDashboardCategory, mapLabel } from '../../../main/labelHelper'

function GuardRailsPosturePage() {
    const dashboardCategory = getDashboardCategory();
    return (
        <PageWithMultipleCards
            title={
                <Text variant='headingLg'>
                    {mapLabel("API Security Posture",dashboardCategory)}
                </Text>
            }
            isFirstPage={true}
        />
    )
}

export default GuardRailsPosturePage