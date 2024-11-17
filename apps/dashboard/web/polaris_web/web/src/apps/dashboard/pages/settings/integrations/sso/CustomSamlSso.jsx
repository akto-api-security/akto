import React, { useEffect, useState } from 'react'
import StepsComponent from '../components/StepsComponent';
import { Button, Form, FormLayout, InlineStack, LegacyCard, Link, Tag, Text, TextField, BlockStack } from '@shopify/polaris';
import FileUpload from '../../../../components/shared/FileUpload';
import SpinnerCentered from '../../../../components/progress/SpinnerCentered';
import IntegrationsLayout from '../IntegrationsLayout';
import DeleteModal from '../components/DeleteModal';
import func from "@/util/func"
import Details from '../components/Details';
import { XIcon } from "@shopify/polaris-icons";

function CustomSamlSso({ssoType,entityTitle, entityId, loginURL,pageTitle, signinUrl, integrationSteps, cardContent, handleSubmitOutSide, handleDeleteOutside, samlUrlDocs, loading, showCustomInputs, certificateName}) {
    const [componentType, setComponentType] = useState(0) ;
    const [showDeleteModal, setShowDeleteModal] = useState(false);
    const [files, setFiles] = useState(null)
    const [ssoUrl, setSSOUrl] = useState('')
    const [identifier, setIdentifier] = useState('')

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
        handleSubmitOutSide(files, ssoUrl, identifier)
        setComponentType(2)
    }

    useEffect(() => {
        if((loginURL !== null || entityId !== null) && (loginURL?.length > 0 || entityId?.length > 0)){
            setComponentType(2)
        }
    },[loginURL,entityId])

    const formComponent = (
        <LegacyCard.Section>
            <Form onSubmit={handleSubmit}>
                <FormLayout>
                    <BlockStack gap={"400"}>
                        {showCustomInputs ? 
                            <BlockStack gap={"300"}>
                                <TextField label={<Text fontWeight="medium" variant="bodySm">Enter sso url</Text>} 
                                    placeholder='Enter your SSO url'
                                    onChange={setSSOUrl}
                                    value={ssoUrl}
                                />
                                <TextField label={<Text fontWeight="medium" variant="bodySm">Enter Entity Id</Text>} 
                                    placeholder='Enter your Entity Id'
                                    onChange={setIdentifier}
                                    value={identifier}
                                />
                            </BlockStack> : null
                        }
                        <InlineStack gap="300">
                            {files ? 
                                <Tag>
                                    <InlineStack gap={100}>
                                        <Text variant="bodyMd" fontWeight="medium">{files.name}</Text>
                                        <Button onClick={() => setFiles(null)}  icon={XIcon} variant="plain" />
                                    </InlineStack>
                                </Tag>
                            : <Text variant="bodyLg" fontWeight="medium" color="subdued">{"Drop your " + certificateName + " file here."}</Text>}
                            <FileUpload fileType="file" acceptString=".xml" setSelectedFile={setFilesCheck} allowMultiple={false} />
                        </InlineStack>
                        <InlineStack align="end">
                            <Button submit  size="medium" variant="primary">Submit</Button>
                        </InlineStack>
                    </BlockStack>
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
            title: 'SSO url by IdP',
            value: loginURL,
        },
        {
            title: 'Assertion consumer URL',
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

    const useCardContent = (
        <BlockStack gap={"200"}>
            <Text>{cardContent}</Text>
            <InlineStack gap={"100"}>
                <Text>Use</Text>
                <Link>https://app.akto.io/sso-login</Link>
                <Text>for signing into AKTO dashboard via SSO.</Text>
            </InlineStack>
        </BlockStack>
    )
    
    return (
        <>
            <IntegrationsLayout title={pageTitle} cardContent={useCardContent} component={azureSSOComponent} docsUrl={"https://docs.akto.io/sso/" + samlUrlDocs} />
            <DeleteModal setShowDeleteModal={setShowDeleteModal} showDeleteModal={showDeleteModal} SsoType={ssoType} onAction={handleDelete} />
        </>
    )
}

export default CustomSamlSso