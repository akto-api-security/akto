import { LegacyCard, Text, TextField, VerticalStack } from '@shopify/polaris';
import React, { useState, useEffect } from 'react'
import PasswordTextField from '../../../components/layouts/PasswordTextField';
import IntegrationsLayout from './IntegrationsLayout';
import settingRequests from '../api';
import func from '@/util/func'

function CloudflareWaf() {
    const [accountOrZoneId, setAccountOrZoneId] = useState('');
    const [apiKey, setApiKey] = useState('');
    const [email, setEmail] = useState('');

    const wafCard = (
        <LegacyCard
            primaryFooterAction={{content: 'Save', onAction: () => addCloudflareWafIntegration()}}
        >
          <LegacyCard.Section>
            <Text variant="headingMd">Integrate Cloudflare-WAF</Text>
          </LegacyCard.Section>

          <LegacyCard.Section>
            <VerticalStack gap={"2"}>
                <TextField value={email} onChange={setEmail} label="Cloudflare email" placeholder="john@akto.io"/>
                {apiKey !== null ? <PasswordTextField text={apiKey}
                                    setField={setApiKey} onFunc={true} field={apiKey} 
                                    label="Cloudflare API Key"
                />:<></>}
                <TextField value={accountOrZoneId} onChange={setAccountOrZoneId} label="Account ID or Zone ID" placeholder=""/>
            </VerticalStack>
          </LegacyCard.Section> 
        </LegacyCard>
    )

    async function addCloudflareWafIntegration(){
      await settingRequests.addCloudflareWafIntegration(accountOrZoneId, apiKey, email)
      func.setToast(true, false, "Successfully added Cloudflare Waf Integration")
      fetchIntegration()
    }

    async function fetchIntegration() {
      const resp = await settingRequests.fetchCloudflareWafIntegration()
      if(resp != null && resp?.cloudflareWafConfig) {
        setApiKey(null)
      } else {
        setApiKey("")
      }
      setEmail(resp?.cloudflareWafConfig?.email || "")
      setAccountOrZoneId(resp?.cloudflareWafConfig?.accountOrZoneId || "")
    }

    useEffect(() => {
      fetchIntegration()
    }, [])

    let cardContent = "Seamlessly enhance your web application security with Cloudflare-WAF integration, empowering you to efficiently detect vulnerabilities, analyze and intercept web traffic, and fortify your digital defenses. "
    return (
        <IntegrationsLayout title= "Cloudflare WAF" cardContent={cardContent} component={wafCard} docsUrl=""/> 
    )
}

export default CloudflareWaf