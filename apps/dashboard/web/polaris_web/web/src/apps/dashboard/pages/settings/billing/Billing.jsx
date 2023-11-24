import { Box, Card, Divider, LegacyCard, Page, Text } from '@shopify/polaris'
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

function Billing() {
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