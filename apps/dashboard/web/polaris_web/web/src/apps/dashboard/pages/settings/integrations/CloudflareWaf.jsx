import { LegacyCard, Text, TextField, VerticalStack } from '@shopify/polaris';
import React, { useState, useEffect } from 'react'
import PasswordTextField from '../../../components/layouts/PasswordTextField';
import IntegrationsLayout from './IntegrationsLayout';
import settingRequests from '../api';
import func from '@/util/func'
import DropdownSearch from '../../../components/shared/DropdownSearch';
import SeverityLevelDropdown from '../../../components/shared/SeverityLevelDropdown';

function CloudflareWaf() {
    const [accountOrZoneId, setAccountOrZoneId] = useState('');
    const [apiKey, setApiKey] = useState('');
    const [integrationType, setIntegrationType] = useState('zones');
    const [email, setEmail] = useState('');
    const [zoneId, setZoneId] = useState('');
    const [severityLevels, setSeverityLevels] = useState(['CRITICAL']);

    const isZoneLevel = integrationType === "zones";

    const wafCard = (
        <LegacyCard
            primaryFooterAction={{content: 'Save', onAction: () => addCloudflareWafIntegration()}}
            secondaryFooterActions={[{content: 'Delete', onAction: () => deleteCloudflareWafIntegration()}]}
        >
          <LegacyCard.Section>
            <Text variant="headingMd">Integrate Cloudflare-WAF</Text>
          </LegacyCard.Section>

          <LegacyCard.Section>
            <VerticalStack gap={"2"}>
                <TextField value={email} onChange={setEmail} label="Cloudflare email (leave empty for API Token)" placeholder="john@akto.io"/>
                {apiKey !== null ? <PasswordTextField text={apiKey}
                                    setField={setApiKey} onFunc={true} field={apiKey}
                                    label="Cloudflare API Key / API Token"
                />:<></>}
                <DropdownSearch
                    label={"WAF Rule Level"}
                    placeholder={"Select WAF rule level"}
                    optionsList={[{label: "Zone (Cloudflare - All Plans)", value: "zones"}, {label: "Account (Cloudflare - Enterprise Only)", value: "accounts"}]}
                    setSelected={setIntegrationType}
                    preSelected={integrationType}
                    value={integrationType}
                />
                <TextField value={accountOrZoneId} onChange={setAccountOrZoneId} label="Cloudflare Account ID" placeholder="Account ID (required for IP list)"/>
                {isZoneLevel && (
                    <TextField value={zoneId} onChange={setZoneId} label="Cloudflare Zone ID" placeholder="Zone ID (required for WAF rule)"/>
                )}
                <SeverityLevelDropdown
                  severityLevels={severityLevels}
                  setSeverityLevels={setSeverityLevels}
                />
            </VerticalStack>
          </LegacyCard.Section>
        </LegacyCard>
    )

    async function addCloudflareWafIntegration(){
      await settingRequests.addCloudflareWafIntegration(accountOrZoneId, apiKey, email, integrationType, zoneId, severityLevels)
      func.setToast(true, false, "Successfully added Cloudflare Waf Integration")
      fetchIntegration()
    }

    async function deleteCloudflareWafIntegration() {
        await settingRequests.deleteCloudflareWafIntegration()
        func.setToast(true, false, "Successfully deleted Cloudflare Waf Integration")
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
      setIntegrationType(resp?.cloudflareWafConfig?.integrationType || "zones")
      setZoneId(resp?.cloudflareWafConfig?.zoneId || "")
      setSeverityLevels(resp?.cloudflareWafConfig?.severityLevels || ['CRITICAL'])
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