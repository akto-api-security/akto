import { Link } from '@shopify/polaris'
import React, { useEffect, useState } from 'react'
import CopyCommand from '../../../../components/shared/CopyCommand'
import CustomSamlSso from './CustomSamlSso';
import settingRequests from '../../api';

function GoogleSamlSso() {

    const location = window.location ;
    const hostname = location.origin;
    const entityId = hostname ;
    const AcsUrl = hostname + "/callback-google-saml";

    const [loading, setLoading] = useState(false)

    const [loginUrl, setLoginUrl] = useState('')
    const [ssoIdentity, setSsoIdentity] = useState('')

    const cardContent = "Enable Login via Google Workspace on your Akto dashboard";

    const integrationSteps = [
        {
            text: "Go to your Google admin console.",
            component: <Link url="https://admin.google.com/" target="_blank">Click here</Link>
        },
        {
            text: "Click on Apps first and then Web and mobile apps. Select Add custom SAML app from the drop-down Add App menu.",
        },
        {
            text: "Enter name of the app and choose a suitable icon and then click continue.",
        },
        {
            text: "From 'Option 1', copy 'SSO URL' and 'Entity ID' and download the certificate and click Next",
        },
        {
            text: "In 'Assertion Consumer Service URL', fill the below text.",
            component: <CopyCommand command={AcsUrl} />
        },
        {
            text: "In 'Entity ID', fill the below text",
            component: <CopyCommand command={entityId} />
        },
        {
            text: "Select 'Basic information' in first dropdown and 'Primary Email' in second dropdown and click on next."
        },
        {
            text: "In the Attribute Mapping screen, click Add New Mapping, Set the 'Primary email' = email and make sure that the 'Service Status Button' is on."
        }
    ]
    
    const handleSubmit = async(files, ssoUrl, identifier) => {
        setLoading(true)
        await settingRequests.addAzureSso(ssoUrl ,files.content, identifier, entityId, AcsUrl, "GOOGLE_SAML");
        fetchData();
    }

    const fetchData = async () => {
        try {
            setLoading(true)
            await settingRequests.fetchAzureSso("GOOGLE_SAML").then((resp)=> {
                setLoginUrl(resp.loginUrl)
                setSsoIdentity(resp.ssoEntityId)
            })
            setLoading(false)
        } catch (error) {
            setLoading(false)
        }
    }

    useEffect(() => {
        fetchData()
    },[])

    const handleDelete = async() => {
        await settingRequests.deleteAzureSso("GOOGLE_SAML")
    }
    
    return (
        <CustomSamlSso
            ssoType={"Google Workspace"}
            entityTitle="Google identity information"
            entityId={ssoIdentity}
            loginURL={loginUrl}
            integrationSteps={integrationSteps}
            cardContent={cardContent}
            handleSubmitOutSide={handleSubmit}
            handleDeleteOutside={handleDelete}
            samlUrlDocs={''}
            pageTitle={"Google Workspace SSO SAML"}
            loading={loading}
            showCustomInputs={true}
            certificateName={"X509 certificate"}
            signinUrl={AcsUrl}
        />
    )
}

export default GoogleSamlSso