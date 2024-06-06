import { TextField } from '@shopify/polaris';
import { useState, useEffect } from 'react';
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

import api from './api'
import "./billing.css"
import func from "@/util/func"

const PlanDetails = ({customerId}) => {

    let [customerToken, setCustomerToken] = useState()
    let [orgId, setOrgId] = useState(customerId)
    let [isLoading, setIsLoading] = useState(true)

    const { stigg, refreshData } = useStiggContext();

    async function getToken(orgId) {
        setIsLoading(true)
        await api.getCustomerStiggDetails({customerId: orgId}).then(resp => {
            let customerToken = resp.customerToken
            if (customerToken) {
                setCustomerToken(customerToken)
                stigg.setCustomerId(orgId, customerToken)
            }
            setIsLoading(false)
        })
    }

    useEffect(() => {
        getToken(orgId)
    }, [orgId])



    return (<>{customerToken && <>
            <LegacyCard title="Usage for your plan">
                <Divider />
                <LegacyCard.Section><Box>
                    <CustomerPortalProvider>
                        <CustomerUsageData />
                        <PaymentDetailsSection />
                        <SubscriptionsOverview />
                        <AddonsList/>
                        <Promotions/>
                        <InvoicesSection/>
                    </CustomerPortalProvider>
                </Box></LegacyCard.Section>
                <LegacyCard.Section subdued>
                    For any help, please reach out to support@akto.io
                </LegacyCard.Section>
            </LegacyCard>

            <LegacyCard title="Available plans">
                <Divider />
                <LegacyCard.Section><Box>
                    <Paywall
                        productId="product-akto"
                        onPlanSelected={async ({ plan, customer, intentionType, selectedBillingPeriod }) => {
                            switch (intentionType) {

                                case SubscribeIntentionType.REQUEST_CUSTOM_PLAN_ACCESS:
                                  window.location.href = "https://calendly.com/ankita-akto/akto-demo?month=2023-11"
                                  break;
                                case SubscribeIntentionType.CHANGE_UNIT_QUANTITY:
                                case SubscribeIntentionType.UPGRADE_PLAN:
                                case SubscribeIntentionType.DOWNGRADE_PLAN:
                                    const checkoutResult = await api.provisionSubscription({
                                      billingPeriod: selectedBillingPeriod,
                                      customerId: customer.id,
                                      planId: plan.id,
                                      successUrl: window.location.href+"?orgId="+orgId,
                                      cancelUrl: window.location.href+"?orgId="+orgId
                                    });

                                    if (window?.Intercom) {
                                       window.Intercom("trackEvent", "clicked-upgrade", {"intention": intentionType, "installation": "self-hosted"})
                                    }


                                    console.log("checkoutResult", checkoutResult);
                                    if (checkoutResult.data.provisionSubscription.status === 'PAYMENT_REQUIRED') {
                                      window.location.href = checkoutResult.data.provisionSubscription.checkoutUrl;
                                    } else {
                                        refreshData()
                                        console.log("some error happened!")
                                    }
                                  break;
                            }
                        }}
                    />
                </Box></LegacyCard.Section>
                <LegacyCard.Section subdued>
                    For any help, please reach out to support@akto.io
                </LegacyCard.Section>
            </LegacyCard>
            </>
    }
    {isLoading && <div>Checking org...</div>}
    {!isLoading && !customerToken && <div>Invalid org id</div>}
    </>)
}

const SelfHosted = () => {

    function checkValidOrgId(customerId) {
        const uuidRegex = /^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$/;

        return uuidRegex.test(customerId)
    }

    const searchParams = new URLSearchParams(document.location.search)
    let [customerId, setCustomerId] = useState(searchParams.get("orgId") || searchParams.get("customerId") || "")


    function removeSearchParams() {
        const newUrl = window.location.path
        window.history.pushState({path:newUrl},'',newUrl);
    }

    useEffect(() => {
        const checkoutCompleted = searchParams.get('checkoutCompleted')
        switch (checkoutCompleted) {
            case "true":
                func.setToast(true,  false, `Checkout completed successfully!`)
                removeSearchParams();

                break;
            case "false":
                func.setToast(true,  true, `There was an error during checkout!`)
                removeSearchParams();
                break;

            default:
        }
    })

    return (<Page
            title="Self hosted plans"
            divider
        >
        {
            !checkValidOrgId(customerId) &&
            <LegacyCard title="Available plans">
                <Divider />
                <LegacyCard.Section><Box>

                    <TextField
                        label="Organization ID"
                        value={customerId}
                        placeholder="416b746f-6973-746f-6f61-7765736f6d65"
                        onChange={(customerId) => setCustomerId(customerId)}
                        autoComplete="off"
                    />
                </Box></LegacyCard.Section>
                <LegacyCard.Section subdued>
                    For any help, please reach out to support@akto.io
                </LegacyCard.Section>
            </LegacyCard>

        }
        {checkValidOrgId(customerId) && <StiggProvider apiKey={window.STIGG_CLIENT_KEY}><PlanDetails customerId={customerId}/></StiggProvider>}
        </Page>
    )

}

export default SelfHosted