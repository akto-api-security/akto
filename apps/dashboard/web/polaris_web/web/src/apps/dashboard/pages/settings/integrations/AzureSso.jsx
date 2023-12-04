import React, { useState } from 'react'
import IntegrationsLayout from './IntegrationsLayout';
import SpinnerCentered from '../../../components/progress/SpinnerCentered';

function AzureSso() {

    const location = window.location ;
    const hostname = location.origin;
    const entityId = hostname ;
    const AcsUrl = hostname + "/signup-azure-saml"
    
    const [certificate, setCertificate] = useState('');
    const [xmlFile, setXmlFile] = useState('');
    const [componentType, setComponentType] = useState(0) ;
    const [loading, setLoading] = useState(false)

    const cardContent = "Enable Login via Azure AD on your Akto dashboard";

    const integrationSteps = [
        {
            text: "Go to your Okta admin console. Go inside 'Applications' tab and click on 'Create App Integration' button.",
        },
        {
            text: "In 'Sign-in Method', choose 'OIDC - OpenID Connect' and in 'Application type', choose 'Web Application'.",
        },
        {
            text: "In 'App integration name' field, fill 'Akto'.",
        },
        {
            text: "In 'Sign-in redirect URIs' field, fill the below URL below",
            component: <CopyCommand command={redirectUri} />
        },
        {
            text: "In 'Assignments' choose the access you required and then click on 'Save'."
        },
        {
            text: "Copy the 'CLIENT_ID' and 'CLIENT_SECRET'."
        }
    ]

    const oktaSSOComponent = (
        loading ? <SpinnerCentered /> :
        <LegacyCard title="Azure AD SSO">
            {componentType === 0 ? stepsComponent : componentType === 1 ? formComponent : valueComponent }
        </LegacyCard>
    )
    
    return (
        <>
            <IntegrationsLayout title="GitHub SSO" cardContent={cardContent} component= docsUrl="" />
        </>
    )
}

export default AzureSso