import { Box, Card, Divider, LegacyCard, Page, Text, Button, HorizontalStack } from '@shopify/polaris'
import { Paywall, StiggProvider, SubscribeIntentionType, useStiggContext } from '@stigg/react-sdk'
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
import func from "@/util/func"

import "./billing.css"

function Billing() {

    const { stigg, refreshData } = useStiggContext();

    const searchParams = new URLSearchParams(document.location.search)

    function removeSearchParams() {
        const newUrl = '/dashboard/settings/billing'
        window.history.pushState({path:newUrl},'',newUrl);
    }

    useEffect(() => {
        const checkoutCompleted = searchParams.get('checkoutCompleted')
        switch (checkoutCompleted) {
            case "true":
                func.setToast(true,  false, `Checkout completed successfully!`)
                removeSearchParams();
                refreshData();

                break;
            case "false":
                func.setToast(true,  true, `There was an error during checkout!`)
                removeSearchParams();
                refreshData();
                break;

            default:
        }
    })

    async function refreshUsageData(){
        await billingApi.refreshUsageData({organizationId: window.STIGG_CUSTOMER_ID})
    }


    const usageTitle = (
        <div>
            <Box paddingBlockEnd="4">
                <Text variant="headingMd">Your plan</Text>
            </Box>
            <Button onClick={() => refreshUsageData()}>
                Sync usage data
            </Button>
        </div>
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
                    productId="product-akto-saa-s"
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