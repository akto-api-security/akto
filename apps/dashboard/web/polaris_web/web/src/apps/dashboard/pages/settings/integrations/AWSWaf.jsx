import { LegacyCard, Text, TextField, VerticalStack } from '@shopify/polaris';
import React, { useState, useEffect } from 'react'
import PasswordTextField from '../../../components/layouts/PasswordTextField';
import IntegrationsLayout from './IntegrationsLayout';
import settingRequests from '../api';
import func from '@/util/func'
import { usePermissions, whenAllowed } from '@/util/permissions'
import SeverityLevelDropdown from '../../../components/shared/SeverityLevelDropdown';
import ThreatPoliciesDropdown from '../../../components/shared/ThreatPoliciesDropdown';

function AWSWaf() {
    const { canCall } = usePermissions()
    const [accessKey, setAccessKey] = useState('');
    const [secretKey, setSecretKey] = useState('');
    const [region, setRegion] = useState('');
    const [ruleSetId, setRuleSetId] = useState('');
    const [ruleSetName, setRuleSetName] = useState('');
    const [isPresent, setIsPresent] = useState(false);
    const [severityLevels, setSeverityLevels] = useState(['CRITICAL']);
    const [threatPolicies, setThreatPolicies] = useState([]);
    const wafCard = (
        <LegacyCard
            primaryFooterAction={{content: 'Save', onAction: () => addAwsWafIntegration(), ...whenAllowed(canCall('api/addAwsWafIntegration'))}}
            secondaryFooterActions={[{content: 'Delete', onAction: () => deleteAwsWafIntegration(), ...whenAllowed(canCall('api/deleteCloudflareWafIntegration'))}]}
        >
          <LegacyCard.Section>
            <Text variant="headingMd">Integrate AWS-WAF</Text>
          </LegacyCard.Section>

          <LegacyCard.Section>
            <VerticalStack gap={"2"}>
                <TextField value={accessKey} onChange={setAccessKey} label="AWS access key ID" placeholder="Access key ID"/>
                <PasswordTextField text={secretKey}
                                    setField={setSecretKey} onFunc={true} field={secretKey}
                                    label="AWS secret access key"
                />
                <TextField value={region} onChange={setRegion} label="AWS region" placeholder="us-east-1" helpText="Region of the IP set. It must be the same region as the Web ACL protecting your ALB or API Gateway."/>
                {ruleSetId ?
                  <Text variant="bodyMd" color="subdued">
                    {`Blocked IPs are added to IP set ${ruleSetName} (${ruleSetId}). Add a rule with action Block referencing this IP set to the Web ACL protecting your ALB or API Gateway.`}
                  </Text> :
                  <Text variant="bodyMd" color="subdued">
                    On save, Akto uses the IP set akto-blocked-ips in this region and creates it if it doesn't exist.
                  </Text>
                }
                <SeverityLevelDropdown
                  severityLevels={severityLevels}
                  setSeverityLevels={setSeverityLevels}
                />
                <ThreatPoliciesDropdown
                  threatPolicies={threatPolicies}
                  setThreatPolicies={setThreatPolicies}
                />
            </VerticalStack>
          </LegacyCard.Section>
        </LegacyCard>
    )

    async function addAwsWafIntegration(){
      await settingRequests.addAwsWafIntegration(accessKey, secretKey, region, severityLevels, threatPolicies)
      window.IS_AWS_WAF_INTEGRATED = 'true'
      func.setToast(true, false, "Successfully added Aws Waf Integration")
      fetchIntegration()
    }

    async function deleteAwsWafIntegration() {
      await settingRequests.deleteCloudflareWafIntegration('AWS_WAF')
      window.IS_AWS_WAF_INTEGRATED = 'false'
      func.setToast(true, false, "Successfully deleted Aws Waf Integration")
      fetchIntegration()
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
      setThreatPolicies(resp?.wafConfig?.threatPolicies || [])
    }

    useEffect(() => {
      fetchIntegration()
      // fetch call
      // bool set
    }, [])

    let cardContent = "Seamlessly enhance your web application security with AWS-WAF integration, empowering you to efficiently detect vulnerabilities, analyze and intercept web traffic, and fortify your digital defenses. "
    return (
        <IntegrationsLayout title= "AWS WAF" cardContent={cardContent} component={wafCard} docsUrl="" readOnly={!canCall('api/addAwsWafIntegration')}/> 
    )
}

export default AWSWaf;