import React, { useState } from 'react'
import StepsComponent from '../components/StepsComponent';
import { Button, Form, FormLayout, HorizontalStack, LegacyCard, Tag, Text, TextField, VerticalStack } from '@shopify/polaris';
import FileUpload from '../../../../components/shared/FileUpload';
import SpinnerCentered from '../../../../components/progress/SpinnerCentered';
import IntegrationsLayout from '../IntegrationsLayout';
import DeleteModal from '../components/DeleteModal';
import func from "@/util/func"
import Details from '../components/Details';
import { CancelMajor } from "@shopify/polaris-icons"

function CustomSamlSso({entityTitle, entityId, loginURL,pageTitle, signinUrl, integrationSteps, cardContent, handleSubmitOutSide, handleDeleteOutside, samlUrlDocs, loading, showSSOUrl, certificateName}) {
    const [componentType, setComponentType] = useState(0) ;
    const [showDeleteModal, setShowDeleteModal] = useState(false);
    const [files, setFiles] = useState(null)
    const [ssoUrl, setSSOUrl] = useState('')

    const stepsComponent = (
        <StepsComponent integrationSteps={integrationSteps} onClickFunc={() => setComponentType(1)} buttonActive={true}/>
    )

    const setFilesCheck = (file) => {
        var reader = new FileReader()
        reader.readAsText(file)
        reader.onload = async () => {
            setFiles({content: reader.result, name: file.name})
        }
    }

    const handleSubmit = () => {
        handleSubmitOutSide(files, ssoUrl)
        setComponentType(2)
    }

    const formComponent = (
        <LegacyCard.Section>
            <Form onSubmit={handleSubmit}>
                <FormLayout>
                    <VerticalStack gap={"4"}>
                        {showSSOUrl ? 
                            <TextField label={<Text fontWeight="medium" variant="bodySm">Enter sso url</Text>} 
                                placeholder='Enter your SSO url'
                                onChange={setSSOUrl}
                                value={ssoUrl}
                            /> : null
                        }
                        <HorizontalStack gap="3">
                            {files ? 
                                <Tag>
                                    <HorizontalStack gap={1}>
                                        <Text variant="bodyMd" fontWeight="medium">{files.name}</Text>
                                        <Button onClick={() => setFiles(null)} plain icon={CancelMajor} />
                                    </HorizontalStack>
                                </Tag>
                            : <Text variant="bodyLg" fontWeight="medium" color="subdued">{"Drop your " + certificateName + " file here."}</Text>}
                            <FileUpload fileType="file" acceptString=".xml" setSelectedFile={setFilesCheck} allowMultiple={false} />
                        </HorizontalStack>
                        <HorizontalStack align="end">
                            <Button submit primary size="medium">Submit</Button>
                        </HorizontalStack>
                    </VerticalStack>
                </FormLayout>
            </Form>
        </LegacyCard.Section>
    )

    const handleDelete = async() => {
        handleDeleteOutside()
        func.setToast(true,false, "Azure SSO credentials deleted successfully.")
        setShowDeleteModal(false)
        setComponentType(0)
    }

    const listValues = [
        {
            title: 'Login Url',
            value: loginURL,
        },
        {
            title: 'Sign on Url',
            value: signinUrl,
        },
        {
            title: entityTitle,
            value: entityId,
        }
    ]

    const azureSSOComponent = (
        loading ? <SpinnerCentered /> :
        <LegacyCard title={pageTitle}>
            {componentType === 0 ? stepsComponent : componentType === 1 ? formComponent : <Details values={listValues} onClickFunc={() => setShowDeleteModal(true)} /> }
        </LegacyCard>
    )
    
    return (
        <>
            <IntegrationsLayout title={pageTitle} cardContent={cardContent} component={azureSSOComponent} docsUrl={"https://docs.akto.io/sso/" + samlUrlDocs} />
            <DeleteModal setShowDeleteModal={setShowDeleteModal} showDeleteModal={showDeleteModal} SsoType={"Azure"} onAction={handleDelete} />
        </>
    )
}

export default CustomSamlSso