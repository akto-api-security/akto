import React, { useEffect, useState } from 'react'
import settingFunctions from '../../module';
import settingRequests from '../../api';
import CustomSamlSso from './CustomSamlSso';
import CopyCommand from '../../../../components/shared/CopyCommand';

function AzureSso() {

    const location = window.location ;
    const hostname = location.origin;
    const entityId = hostname ;
    const AcsUrl = hostname + "/signup-azure-saml";
    const signonUrl = hostname + "/signup-azure-request";
    const [loading, setLoading] = useState(false)

    const [loginUrl, setLoginUrl] = useState('')
    const [azureIdentity, setAzureIdentity] = useState('')


    const cardContent = "Enable Login via Azure AD on your Akto dashboard";

    const orgName = window.USER_NAME?.split('@')?.[1]

    const integrationSteps = [
        {
            text: "Go to your Azure AD home page. Go inside 'Enterprise Applications' click on 'Create your own Application' button.",
        },
        {
            text: "You will see a tab in right. Write Application name 'Akto-Sign-up' and click on 3rd button which has 'Non-gallery' option.",
        },
        {
            text: "In getting started page, Assign users and groups accordingly.",
        },
        {
            text: "Next click on 'Set up single sign on' and then click on 'SAML'.",
        },
        {
            text: "In 'Entity ID', fill the below text",
            component: <CopyCommand command={entityId} />
        },
        {
            text: "In 'Assertion Consumer Service URL', fill the below text.",
            component: <CopyCommand command={AcsUrl} />
        },
        {
            text: "In 'Relay State', fill the below text and then click on save.",
            component: <CopyCommand command={orgName} />
        },
        {
            text: "Download the Federation Metadata XML file."
        }
    ]

    const handleSubmit = async(files) => {
        const infoObj = settingFunctions.getParsedXml(files.content)
        setLoading(true)
        await settingRequests.addAzureSso(infoObj.loginUrl, infoObj.certificate, infoObj.entityId, entityId, AcsUrl, "AZURE");
        fetchData();
    }

    const fetchData = async () => {
        try {
            setLoading(true)
            await settingRequests.fetchAzureSso("AZURE").then((resp)=> {
                setLoginUrl(resp.loginUrl)
                setAzureIdentity(resp.ssoEntityId)
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
        await settingRequests.deleteAzureSso("AZURE")
    }
    
    return (
        <CustomSamlSso
            ssoType={"AZURE"}
            entityTitle="Microsoft Entra Identifier"
            entityId={azureIdentity}
            loginURL={loginUrl}
            signinUrl={AcsUrl}
            integrationSteps={integrationSteps}
            cardContent={cardContent}
            handleSubmitOutSide={handleSubmit}
            handleDeleteOutside={handleDelete}
            samlUrlDocs={'azuread-saml'}
            pageTitle={"Azure AD SSO SAML"}
            loading={loading}
            certificateName={"Federation Metadata XML"}
        />
    )
}

export default AzureSso