import { LegacyCard, Text, TextField, VerticalStack } from '@shopify/polaris';
import React, { useState, useEffect } from 'react'
import PasswordTextField from '../../../components/layouts/PasswordTextField';
import IntegrationsLayout from './IntegrationsLayout';
import settingRequests from '../api';
import func from '@/util/func'
import SeverityLevelDropdown from '../../../components/shared/SeverityLevelDropdown';

function AWSWaf() {
    const [accessKey, setAccessKey] = useState('');
    const [secretKey, setSecretKey] = useState('');
    const [region, setRegion] = useState('');
    const [ruleSetId, setRuleSetId] = useState('');
    const [ruleSetName, setRuleSetName] = useState('');
    const [isPresent, setIsPresent] = useState(false);
    const [severityLevels, setSeverityLevels] = useState(['CRITICAL']);
    const wafCard = (
        <LegacyCard
            primaryFooterAction={{content: 'Save', onAction: () => addAwsWafIntegration()}}
        >
          <LegacyCard.Section>
            <Text variant="headingMd">Integrate AWS-WAF</Text>
          </LegacyCard.Section>

          <LegacyCard.Section>
            <VerticalStack gap={"2"}>
                <TextField value={accessKey} onChange={setAccessKey} label="AWS Access Key Id" placeholder="AWS-Key"/>
                <PasswordTextField text={secretKey}
                                    setField={setSecretKey} onFunc={true} field={secretKey} 
                                    label="AWS secret Access Key"
                />
                <TextField value={region} onChange={setRegion} label="Region" placeholder="Region"/>
                <TextField value={ruleSetId} onChange={setRuleSetId} label="Waf Rule Set Id" placeholder="Rule-Set=Id"/>
                <TextField value={ruleSetName} onChange={setRuleSetName} label="Waf Rule Set Name" placeholder="Waf-Rule-Set-Name"/>
                <SeverityLevelDropdown
                  severityLevels={severityLevels}
                  setSeverityLevels={setSeverityLevels}
                />
            </VerticalStack>
          </LegacyCard.Section> 
        </LegacyCard>
    )

    async function addAwsWafIntegration(){
      await settingRequests.addAwsWafIntegration(accessKey, secretKey, region, ruleSetId, ruleSetName,severityLevels)
      func.setToast(true, false, "Successfully added Aws Waf Integration")
    }

    async function fetchIntegration() {
      const resp = await settingRequests.fetchAwsWafIntegration()
      //setIsPresent(resp !== null)
      setAccessKey(resp?.wafConfig?.awsAccessKey)
      setSecretKey(resp?.wafConfig?.awsSecretKey)
      setRegion(resp?.wafConfig?.region)
      setRuleSetId(resp?.wafConfig?.ruleSetId)
      setRuleSetName(resp?.wafConfig?.ruleSetName)
      setSeverityLevels(resp?.wafConfig?.severityLevels || ['CRITICAL'])
    }

    useEffect(() => {
      fetchIntegration()
      // fetch call
      // bool set
    }, [])

    let cardContent = "Seamlessly enhance your web application security with AWS-WAF integration, empowering you to efficiently detect vulnerabilities, analyze and intercept web traffic, and fortify your digital defenses. "
    return (
        <IntegrationsLayout title= "AWS WAF" cardContent={cardContent} component={wafCard} docsUrl=""/> 
    )
}

export default AWSWaf;