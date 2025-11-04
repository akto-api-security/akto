import React, { useEffect, useState } from 'react'
import CopyCommand from '../../../components/shared/CopyCommand';
import IntegrationsLayout from './IntegrationsLayout';
import { Button, Form, FormLayout, HorizontalStack, LegacyCard, Link, Text, TextField, VerticalStack } from '@shopify/polaris';
import func from "@/util/func"
import settingRequests from '../api';
import SpinnerCentered from "../../../components/progress/SpinnerCentered"
import StepsComponent from './components/StepsComponent';
import Details from './components/Details';
import DeleteModal from './components/DeleteModal';

function OktaIntegration() {

    const location = window.location ;
    const hostname = location.origin;

    const [componentType, setComponentType] = useState(0) ;
    const [loading, setLoading] = useState(false)

    const [clientId, setClientId] = useState('')
    const [clientSecret, setClientSecret] = useState('')
    const [oktaDomain, setOktaDomain] = useState('')
    const [authorizationServerId, setAuthorizationServerId] = useState('')
    const [showDeleteModal, setShowDeleteModal] = useState(false);
    const [nextButtonActive,setNextButtonActive] = useState(true)

    const redirectUri = hostname + "/authorization-code/callback"
    const initiateLoginUri = hostname + "/okta-initiate-login?accountId=" + window.ACTIVE_ACCOUNT

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
            text: "In 'Sign-in redirect URIs' field, fill the below URL below.",
            component: <CopyCommand command={redirectUri} />
        },
        {
            text: "In 'Initiate login URI' field, fill the below URL below. (Required only if you are using 'Login initiated by Okta' option)",
            component: <CopyCommand command={initiateLoginUri} />
        },
        {
            text: "In 'Assignments' choose the access you required and then click on 'Save'."
        },
        {
            text: "Copy the 'CLIENT_ID' and 'CLIENT_SECRET'."
        }
    ]

    const handleSubmit = async() => {
        if(clientId.length > 0 && clientSecret.length > 0 && oktaDomain.length > 0){
            await settingRequests.addOktaSso(clientId,clientSecret, authorizationServerId, oktaDomain, redirectUri)
            func.setToast(true, false, "Okta SSO fields saved successfully!")
            setComponentType(2)
        }else{
            func.setToast(true, true, "Fill all fields")
        }
    }

    const formComponent = (
        <LegacyCard.Section title="Fill details">
            <Form onSubmit={handleSubmit}>
                <FormLayout>
                    <TextField label={<Text fontWeight="medium" variant="bodySm">Client ID of Okta's Application</Text>} 
                                placeholder='Enter your client Id'
                                onChange={setClientId}
                                value={clientId}
                    />
                    <TextField label={<Text fontWeight="medium" variant="bodySm">Client Secret of Okta's Application</Text>} 
                                placeholder='Enter your client secret'
                                onChange={setClientSecret}
                                value={clientSecret}
                    />
                    <TextField label={<Text fontWeight="medium" variant="bodySm">Authorization server Id of Okta's Application</Text>} 
                                placeholder='Enter your authorization server Id'
                                onChange={setAuthorizationServerId}
                                value={authorizationServerId}
                    />
                    <TextField label={<Text fontWeight="medium" variant="bodySm">Domain name of Okta's Application</Text>} 
                                placeholder="Enter the domain name of your Okta console"
                                onChange={setOktaDomain}
                                value={oktaDomain}
                    />
                    <HorizontalStack align="end">
                        <Button submit primary size="medium">Submit</Button>
                    </HorizontalStack>
                </FormLayout>
            </Form>
        </LegacyCard.Section>
    )

    const fetchData = async() => {
        setLoading(true)
        try {
            await settingRequests.fetchOktaSso().then((resp) => {
                if(resp.clientId !== null && resp.clientId.length > 0){
                    setClientId(resp.clientId)
                    setAuthorizationServerId(resp.authorisationServerId)
                    setOktaDomain(resp.oktaDomain)
                    setComponentType(2)
                }
            })
            setLoading(false)
        } catch (error) {
            setNextButtonActive(false)
            setLoading(false)
        }
        
    }

    const handleDelete = async() => {
        await settingRequests.deleteOktaSso()
        func.setToast(true,false, "Okta SSO credentials deleted successfully.")
        setShowDeleteModal(false)
        setComponentType(0)
    }
    const listValues = [
        {
            title: "Client Id",
            value: clientId
        },
        {
            title: "Authorisation server Id",
            value: authorizationServerId
        },
        {
            title: "Domain name",
            value: oktaDomain
        }
    ]

    useEffect(()=> {
        fetchData()
    },[])

    const cardContent = "Enable login via Okta SSO in your dashboard."

    const useCardContent = (
        <VerticalStack gap={"2"}>
            <Text>{cardContent}</Text>
            <HorizontalStack gap={"1"}>
                <Text>Use</Text>
                <Link>https://app.akto.io/sso-login</Link>
                <Text>for signing into AKTO dashboard via SSO.</Text>
            </HorizontalStack>
        </VerticalStack>
    )

    
    const oktaSSOComponent = (
        loading ? <SpinnerCentered /> :
        <LegacyCard title="Okta SSO">
            {componentType === 0 ? <StepsComponent integrationSteps={integrationSteps} onClickFunc={()=> setComponentType(1)} buttonActive={nextButtonActive}/> 
            : componentType === 1 ? formComponent : <Details values={listValues} onClickFunc={() => setShowDeleteModal(true)} /> }
        </LegacyCard>
    )

    return (
        <>
            <IntegrationsLayout title="Okta SSO" cardContent={useCardContent} component={oktaSSOComponent} docsUrl="https://docs.akto.io/sso/okta-oidc"/>
            <DeleteModal showDeleteModal={showDeleteModal} setShowDeleteModal={setShowDeleteModal} SsoType={"Okta"} onAction={handleDelete} />
        </>
    )
}

export default OktaIntegration