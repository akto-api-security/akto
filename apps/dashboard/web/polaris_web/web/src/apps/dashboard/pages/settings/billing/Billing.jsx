import { Box, Card, Divider, LegacyCard, Page, Text, Button, HorizontalStack } from '@shopify/polaris'
import { Paywall, StiggProvider } from '@stigg/react-sdk'
import {
  CustomerPortalProvider,
  CustomerUsageData,
  PaymentDetailsSection,
  SubscriptionsOverview,
  AddonsList,
  Promotions,
  InvoicesSection
} from '@stigg/react-sdk'

import React, { useEffect, useState } from 'react'
import settingFunctions from '../module'
import billingApi from './api'

function Billing() {
    async function syncUsage() {
        await billingApi.syncUsage()
    }

    const usageTitle = (
        <Box paddingBlockEnd="4">
            <Text variant="headingMd">Your plan</Text>
        </Box>
    )

    const usageInfo = (
            <Box>
                <CustomerPortalProvider>
                    <CustomerUsageData />
                    <PaymentDetailsSection />
                    <SubscriptionsOverview />
                    <AddonsList/>
                    <Promotions/>
                    <InvoicesSection/>
                </CustomerPortalProvider>

            </Box>
    )

    const planTitle = (
        <Box paddingBlockEnd="4">
            <Text variant="headingMd">Switch plan</Text>
        </Box>
    )

    const planInfo = (
            <Box>
                  <Paywall
                    productId="product-akto"
                    onPlanSelected={({ plan, customer, intentionType }) => {
                        console.log(plan, customer, intentionType);
                    }}
                  />
            </Box>
    )

  return (
    <Page
        title="Billing"
        divider
    >
        <HorizontalStack align='end'>
            <Button onClick={syncUsage} style={{color: "white"}} size="small">Sync usage</Button>
        </HorizontalStack>
        <LegacyCard title={usageTitle}>
            <Divider />
            <LegacyCard.Section  >
                {usageInfo}
            </LegacyCard.Section>
            <LegacyCard.Section subdued>
                For any help, please reach out to support@akto.io
            </LegacyCard.Section>
        </LegacyCard>

        <LegacyCard title={planTitle}>
            <Divider />
            <LegacyCard.Section  >
                {planInfo}
            </LegacyCard.Section>
            <LegacyCard.Section subdued>
                For any help, please reach out to support@akto.io
            </LegacyCard.Section>
        </LegacyCard>
    </Page>
  )
}

export default Billing