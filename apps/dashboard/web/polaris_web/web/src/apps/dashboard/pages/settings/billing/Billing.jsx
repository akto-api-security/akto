import { Box, Card, Divider, LegacyCard, Page, Text, Button, HorizontalStack } from '@shopify/polaris'
import { Paywall, StiggProvider, SubscribeIntentionType } from '@stigg/react-sdk'
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

import "./billing.css"

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
                    onPlanSelected={async ({ plan, customer, intentionType, selectedBillingPeriod }) => {
                        console.log(plan, customer, intentionType);
                        switch (intentionType) {

                            case SubscribeIntentionType.REQUEST_CUSTOM_PLAN_ACCESS:
                              window.location.href = "https://calendly.com/ankita-akto/akto-demo?month=2023-11"
                              break;
                            case SubscribeIntentionType.CHANGE_UNIT_QUANTITY:
                            case SubscribeIntentionType.UPGRADE_PLAN:
                            case SubscribeIntentionType.DOWNGRADE_PLAN:
                                const checkoutResult = await billingApi.provisionSubscription({
                                  billingPeriod: selectedBillingPeriod,
                                  customerId: customer.id,
                                  planId: plan.id,
                                  successUrl: window.location.href,
                                  cancelUrl: window.location.href
                                });

                                console.log("checkoutResult", checkoutResult);
                                if (checkoutResult.data.provisionSubscription.status === 'PAYMENT_REQUIRED') {
                                  window.location.href = checkoutResult.data.provisionSubscription.checkoutUrl;
                                } else {
                                    console.log("some error happened!")
                                }
                              break;
                        }
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