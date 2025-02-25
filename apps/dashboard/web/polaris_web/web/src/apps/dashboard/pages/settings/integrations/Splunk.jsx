import React, { useState } from 'react'
import { LegacyCard, Text, TextField, VerticalStack} from '@shopify/polaris';
import IntegrationsLayout from './IntegrationsLayout';
import PasswordTextField from '../../../components/layouts/PasswordTextField';
function Splunk() {
    
    const [splunkUrl, setSplunkUrl] = useState('');
    const [splunkToken, setSplunkToken] = useState('');
    const PostmanCard = (
        <LegacyCard
            primaryFooterAction={{content: 'Save', onAction: () => {}}}
        >
          <LegacyCard.Section>
            <Text variant="headingMd">Integrate Splunk</Text>
          </LegacyCard.Section>

          <LegacyCard.Section>
            <VerticalStack gap={"2"}>
            <TextField value={splunkUrl} onChange={setSplunkUrl} label="Splunk URL" placeholder="Your splunk url"/>
                <PasswordTextField text={splunkToken}
                                    setField={setSplunkToken} onFunc={true} field={splunkToken} 
                                    label="Splunk access token"
                />
            </VerticalStack>
          </LegacyCard.Section> 
        </LegacyCard>
    )

    let cardContent = "Seamlessly enhance your web application security with Splunk integration, empowering you to efficiently detect vulnerabilities, analyze and intercept web traffic, and fortify your digital defenses. "
    return (
        <IntegrationsLayout title= "Splunk SEIM" cardContent={cardContent} component={PostmanCard} docsUrl=""/> 
    )
}

export default Splunk