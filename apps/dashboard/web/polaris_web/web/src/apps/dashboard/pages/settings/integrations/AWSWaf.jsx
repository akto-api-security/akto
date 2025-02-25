import { LegacyCard, Text, TextField, VerticalStack } from '@shopify/polaris';
import React, { useState } from 'react'
import PasswordTextField from '../../../components/layouts/PasswordTextField';
import IntegrationsLayout from './IntegrationsLayout';

function AWSWaf() {
    const [accessKey, setAccessKey] = useState('');
    const [secretKey, setSecretKey] = useState('');
    const wafCard = (
        <LegacyCard
            primaryFooterAction={{content: 'Save', onAction: () => {}}}
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
            </VerticalStack>
          </LegacyCard.Section> 
        </LegacyCard>
    )

    let cardContent = "Seamlessly enhance your web application security with AWS-WAF integration, empowering you to efficiently detect vulnerabilities, analyze and intercept web traffic, and fortify your digital defenses. "
    return (
        <IntegrationsLayout title= "AWS WAF" cardContent={cardContent} component={wafCard} docsUrl=""/> 
    )
}

export default AWSWaf;