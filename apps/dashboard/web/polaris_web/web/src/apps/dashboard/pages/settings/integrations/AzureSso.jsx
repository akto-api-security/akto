import React, { useEffect, useState } from 'react'
import IntegrationsLayout from './IntegrationsLayout';
import SpinnerCentered from '../../../components/progress/SpinnerCentered';
import CopyCommand from '../../../components/shared/CopyCommand';
import { Button, Form, FormLayout, HorizontalStack, LegacyCard, Tag, Text } from '@shopify/polaris';
import { CancelMajor } from "@shopify/polaris-icons"
import FileUpload from '../../../components/shared/FileUpload';
import settingFunctions from '../module';
import settingRequests from '../api';
import func from "@/util/func"
import StepsComponent from './components/StepsComponent';
import DeleteModal from './components/DeleteModal';
import Details from './components/Details';

function AzureSso() {

    const location = window.location ;
    const hostname = location.origin;
    const entityId = hostname ;
    const AcsUrl = hostname + "/signup-azure-saml";
    const signonUrl = hostname + "/signup-azure-request";
    
    const [componentType, setComponentType] = useState(0) ;
    const [loading, setLoading] = useState(false)
    const [showDeleteModal, setShowDeleteModal] = useState(false);
    const [loginUrl, setLoginUrl] = useState('')
    const [azureIdentity, setAzureIdentity] = useState('')
    const [files, setFiles] = useState(null)
    const [nextButtonActive,setNextButtonActive] = useState(window.DASHBOARD_MODE === "ON_PREM")


    const cardContent = "Enable Login via Azure AD on your Akto dashboard";

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
            text: "In 'Sign on URL', fill the below text and then click on save.",
            component: <CopyCommand command={signonUrl} />
        },
        {
            text: "Download the Federation Metadata XML file."
        }
    ]

    const stepsComponent = (
        <StepsComponent integrationSteps={integrationSteps} onClickFunc={() => setComponentType(1)} buttonActive={nextButtonActive}/>
    )

    const setFilesCheck = (file) => {
        var reader = new FileReader()
        reader.readAsText(file)
        reader.onload = async () => {
            setFiles({content: reader.result, name: file.name})
        }
    }

    const handleSubmit = async() => {
        const infoObj = settingFunctions.getParsedXml(files.content)
        setLoading(true)
        await settingRequests.addAzureSso(infoObj.loginUrl, infoObj.certificate, infoObj.entityId, entityId, AcsUrl);
        setComponentType(2)
        fetchData();
        setLoading(false)
    }

    const fetchData = async () => {
        setLoading(true)
        try {
            await settingRequests.fetchAzureSso().then((resp)=> {
                setLoginUrl(resp.loginUrl)
                setAzureIdentity(resp.azureEntityId)
                if(resp.loginUrl == null){
                    setComponentType(0)
                }else{
                    setComponentType(2)
                }
            })
            setLoading(false)
        } catch (error) {
            setLoading(false)
            setNextButtonActive(false)   
        }
    }

    useEffect(() => {
        fetchData()
    },[])

    const formComponent = (
        <LegacyCard.Section>
            <Form onSubmit={handleSubmit}>
                <FormLayout>
                <HorizontalStack gap="3">
                    {files ? 
                        <Tag>
                            <HorizontalStack gap={1}>
                                <Text variant="bodyMd" fontWeight="medium">{files.name}</Text>
                                <Button onClick={() => setFiles(null)} plain icon={CancelMajor} />
                            </HorizontalStack>
                        </Tag>
                    : <Text variant="bodyLg" fontWeight="medium" color="subdued">Drop your Federation Metadata XML file here.</Text>}
                    <FileUpload fileType="file" acceptString=".xml" setSelectedFile={setFilesCheck} allowMultiple={false} />
                </HorizontalStack>
                    <HorizontalStack align="end">
                        <Button submit primary size="medium">Submit</Button>
                    </HorizontalStack>
                </FormLayout>
            </Form>
        </LegacyCard.Section>
    )

    const handleDelete = async() => {
        await settingRequests.deleteAzureSso()
        func.setToast(true,false, "Azure SSO credentials deleted successfully.")
        setShowDeleteModal(false)
        setComponentType(0)
    }

    const listValues = [
        {
            title: 'Login Url',
            value: loginUrl,
        },
        {
            title: 'Sign on Url',
            value: signonUrl,
        },
        {
            title: 'Microsoft Entra Identifier',
            value: azureIdentity,
        }
    ]

    const azureSSOComponent = (
        loading ? <SpinnerCentered /> :
        <LegacyCard title="Azure AD SSO SAML">
            {componentType === 0 ? stepsComponent : componentType === 1 ? formComponent : <Details values={listValues} onClickFunc={() => setShowDeleteModal(true)} /> }
        </LegacyCard>
    )
    
    return (
        <>
            <IntegrationsLayout title="Azure AD SSO SAML" cardContent={cardContent} component={azureSSOComponent} docsUrl="" />
            <DeleteModal setShowDeleteModal={setShowDeleteModal} showDeleteModal={showDeleteModal} SsoType={"Azure"} onAction={handleDelete} />
        </>
    )
}

export default AzureSso