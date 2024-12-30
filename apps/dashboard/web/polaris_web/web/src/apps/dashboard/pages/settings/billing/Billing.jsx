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

        if (window.PLAN_TYPE && window.PLAN_TYPE.length > 0) {
            const checkForElement = () => {
                const elements = document.querySelectorAll('.stigg-subscription-plan-name');
                if (elements.length > 0) {
                    elements.forEach(element => {
                        element.textContent = window.PLAN_TYPE;
                    });
                } else {
                    setTimeout(() => checkForElement(), 500);
                }
            };
            checkForElement();
        }
    })

    async function refreshUsageData(){
        await billingApi.refreshUsageData()
        func.setToast(true, false, `Syncing usage data. Please refresh after some time.`)
        setTimeout(() => {
            window.location.reload();
        }, 10000)
    }


    const usageTitle = (
        <Box paddingBlockEnd="4">
            <HorizontalStack align="space-between" blockAlign="center">
                <Box>
                    <Text variant="headingMd">Your plan</Text>
                </Box>
                <Button onClick={() => refreshUsageData()}>
                    Sync usage data
                </Button>
            </HorizontalStack>
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
                    productId="product-akto-saa-s"
                    onPlanSelected={async ({ plan, customer, intentionType, selectedBillingPeriod }) => {
                        console.log(plan, customer, intentionType);
                        if (window.IS_SAAS !== 'true') {
                            window.location.href = "https://app.akto.io/dashboard/settings/self-hosted"
                            return;
                        }
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

                                if (window?.Intercom) {
                                   window.Intercom("trackEvent", "clicked-upgrade", {"intention": intentionType, "installation": "saas"})
                                }

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

        {window.IS_SAAS === 'true'  && <LegacyCard title={planTitle}>
            <Divider />
            <LegacyCard.Section  >
                {planInfo}
            </LegacyCard.Section>
            <LegacyCard.Section subdued>
                For any help, please reach out to support@akto.io
            </LegacyCard.Section>
        </LegacyCard>}
    </Page>
  )
}

export default Billing