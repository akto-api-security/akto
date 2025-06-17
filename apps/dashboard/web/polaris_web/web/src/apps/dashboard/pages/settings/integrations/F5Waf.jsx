import React, { useState } from 'react'
import IntegrationsLayout from './IntegrationsLayout'
import { LegacyCard, TextField, VerticalStack } from '@shopify/polaris'
import PasswordTextField from '../../../components/layouts/PasswordTextField';
import SeverityLevelDropdown from '../../../components/shared/SeverityLevelDropdown';

function F5Waf() {

    const [serverUrl, setServerUrl] = useState('');
    const [policyName, setPolicyName] = useState('');
    const [userName, setUserName] = useState('');
    const [passWord, setPassWord] = useState('');
    const [severityLevels, setSeverityLevels] = useState(['CRITICAL']);

    const f5Waf = (
        <LegacyCard primaryFooterAction={{content: 'Save', onAction: () => {}}} secondaryFooterActions={[{content: 'Test integration', onAction: () => {}}]}>
            <LegacyCard.Section title="F5 details">
                <VerticalStack gap={"2"}>
                    <TextField onChange={setServerUrl} value={serverUrl} label="F5 server url" placeholder="https://18.140.219:8443/" />
                    <TextField onChange={setPolicyName} value={policyName} label="F5 application policy name" placeholder="AKTO-WAF" />
                </VerticalStack>
            </LegacyCard.Section>
            <LegacyCard.Section title="F5 login credentials">
                <VerticalStack gap={"2"}>
                    <TextField onChange={setUserName} value={userName} label="Username" placeholder="johndoe@waf.com" />
                    <PasswordTextField text={passWord} setField={setPassWord} onFunc={true} field={passWord} label="Password" />
                    <SeverityLevelDropdown
                       severityLevels={severityLevels}
                       setSeverityLevels={setSeverityLevels}
                    />
                </VerticalStack>
            </LegacyCard.Section>
        </LegacyCard>
    ) 

    let cardContent = "Seamlessly enhance your web application security with F5 integration, empowering you to efficiently detect vulnerabilities, analyze and intercept web traffic, and fortify your digital defenses. "
    return (
        <IntegrationsLayout title= "F5-WAF" cardContent={cardContent} component={f5Waf} docsUrl="https://docs.akto.io/"/> 
    )
}

export default F5Waf