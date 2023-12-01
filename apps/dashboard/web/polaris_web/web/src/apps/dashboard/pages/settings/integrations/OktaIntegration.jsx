import React, { useEffect, useState } from 'react'
import CopyCommand from '../../../components/shared/CopyCommand';
import IntegrationsLayout from './IntegrationsLayout';
import { Box, Button, Form, FormLayout, HorizontalStack, LegacyCard, Modal, Text, TextField, VerticalStack } from '@shopify/polaris';
import func from "@/util/func"
import settingRequests from '../api';
import SpinnerCentered from "../../../components/progress/SpinnerCentered"

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

    const redirectUri = hostname + "/authorization-code/callback"

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

    const handleSubmit = async() => {
        if(clientId.length > 0 && clientSecret.length > 0 && oktaDomain.length > 0 && authorizationServerId.length > 0){
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

    const stepsComponent = (
        <LegacyCard.Section title="Follow steps">
            <VerticalStack gap={3}>
                {integrationSteps.map((x,index)=> {
                    return(
                        <VerticalStack gap={2} key={index}>
                            <HorizontalStack gap={1}>
                                <Text fontWeight="semibold" variant="bodyLg">{index + 1}.</Text>
                                <Text variant="bodyLg">{x.text}</Text>
                            </HorizontalStack>
                            {x?.component}
                        </VerticalStack>
                    )
                })}
                <HorizontalStack align="end">
                    <Button primary size="medium" onClick={() => setComponentType(1)}>Next</Button>
                </HorizontalStack>
            </VerticalStack>
        </LegacyCard.Section>
    )

    const fetchData = async() => {
        setLoading(true)
        await settingRequests.fetchOktaSso().then((resp) => {
            if(resp.clientId !== null && resp.clientId.length > 0){
                setClientId(resp.clientId)
                setAuthorizationServerId(resp.authorisationServerId)
                setOktaDomain(resp.oktaDomain)
                setComponentType(2)
            }
        })
        setLoading(false)
    }

    const handleDelete = async() => {
        await settingRequests.deleteOktaSso()
        func.setToast(true,false, "Okta SSO credentials deleted successfully.")
        setShowDeleteModal(false)
        setComponentType(0)
    }

    const modalComponent = (
        <Modal
            open={showDeleteModal}
            onClose={() => setShowDeleteModal(false)}
            title="Are you sure?"
            primaryAction={{
                content: 'Delete Okta SSO',
                onAction: handleDelete
            }}
        >
            <Modal.Section>
                <Text variant="bodyMd">Are you sure you want to remove Okta SSO Integration? This might take away access from existing Akto users. This action cannot be undone.</Text>
            </Modal.Section>
        </Modal>
    )

    function LineComponent({title,value}){
        return(
            <HorizontalStack gap={5}>
                <Box width='180px'>
                    <HorizontalStack align="end">
                        <Text variant="headingSm">{title}: </Text>
                    </HorizontalStack>
                </Box>
                <Text variant="bodyLg">{value}</Text>
            </HorizontalStack>
        )
    }

    const valueComponent = (
        <LegacyCard.Section title="Integration details">
            <br/>
            <VerticalStack gap={3}>
                <VerticalStack gap={2}>
                    <LineComponent title={"Client Id"} value={clientId}/>
                    <LineComponent title={"Authorisation server Id"} value={authorizationServerId} />
                    <LineComponent title={"Domain name"} value={oktaDomain} />
                </VerticalStack>
                <HorizontalStack align="end">
                    <Button primary onClick={()=> setShowDeleteModal(true)} >Delete SSO</Button>
                </HorizontalStack>
            </VerticalStack>
        </LegacyCard.Section>
    )

    useEffect(()=> {
        fetchData()
    },[])

    const cardContent = "Enable login via Okta SSO in your dashboard."
    const oktaSSOComponent = (
        loading ? <SpinnerCentered /> :
        <LegacyCard title="Okta SSO">
            {componentType === 0 ? stepsComponent : componentType === 1 ? formComponent : valueComponent }
        </LegacyCard>
    )

    return (
        <>
        <IntegrationsLayout title="Okta SSO" cardContent={cardContent} component={oktaSSOComponent} />
        {modalComponent}
        </>
    )
}

export default OktaIntegration