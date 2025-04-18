import React, { useEffect, useState } from 'react'
import settingFunctions from '../module'
import IntegrationsLayout from './IntegrationsLayout'
import { Divider, LegacyCard, Text, TextField, VerticalStack } from '@shopify/polaris'
import PasswordTextField from '../../../components/layouts/PasswordTextField'
import func from "@/util/func"

const AzureBoards = () => {
    
    const [baseUrl, setBaseUrl] = useState('')
    const [organization, setOrganization] = useState('')
    const [projectIds, setProjectIds] = useState('')
    const [personalAuthToken, setPersonalAuthToken] = useState('')
    const [isRemoveable, setIsRemoveable] = useState(false)

    async function fetchAzureBoardsInteg() {
        let azureBoardsInteg = await settingFunctions.fetchAzureBoardsIntegration()
        setBaseUrl(azureBoardsInteg != null ? azureBoardsInteg.baseUrl : '')
        setOrganization(azureBoardsInteg != null ? azureBoardsInteg.organization : '')
        setProjectIds(azureBoardsInteg != null ? azureBoardsInteg.projectList?.join(',') : '')
        setPersonalAuthToken(azureBoardsInteg != null ? null : '')
        setIsRemoveable(azureBoardsInteg != null ? true : false)
    }

    useEffect(() => {
        fetchAzureBoardsInteg()
    }, [])

    async function addAzureBoardsIntegration(){
        await settingFunctions.addAzureBoardsIntegration(baseUrl, organization, projectIds.split(','), personalAuthToken)
        func.setToast(true, false, "Successfully added Azure Boards Integration")
        fetchAzureBoardsInteg()
    }

    async function removeAzureBoardsIntegration() {
        await settingFunctions.removeAzureBoardsIntegration()
        func.setToast(true, false, "Successfully removed Azure Boards Integration")
        setOrganization('')
        setProjectIds('')
        setPersonalAuthToken('')
        setIsRemoveable(false)
    }
    
    const AzureBoardsCard = (
        <LegacyCard
            primaryFooterAction={{content: 'Save', onAction: addAzureBoardsIntegration }}
            secondaryFooterActions={[{content: 'Remove', onAction: removeAzureBoardsIntegration, disabled: !isRemoveable}]}
        >
          <LegacyCard.Section>
            <Text variant="headingMd">Integrate Azure Boards</Text>
          </LegacyCard.Section>

          <LegacyCard.Section>
                <VerticalStack gap={"2"}>
                    <TextField label="Azure DevOps Board URL" value={baseUrl} helpText="Specify the base url your azure devops board dashboard. (e.g. https://dev.azure.com or https://{organization}.visualstudio.com)" placeholder='Base url' requiredIndicator onChange={setBaseUrl} />
                    <TextField label="Organization" value={organization} helpText="Specify the organization name" placeholder='Organization name' requiredIndicator onChange={setOrganization} />
                    {personalAuthToken === null ? <></> : <PasswordTextField label="Personal Auth Token" helpText="Specify the personal auth token created for your project" field={personalAuthToken} onFunc={true} setField={setPersonalAuthToken} />}
                    <TextField label="Projects" helpText="Specify the projects names in comma separated string" value={projectIds} placeholder='Project Names' requiredIndicator onChange={setProjectIds} />
                </VerticalStack>
          </LegacyCard.Section> 
          <Divider />
          <br/>
        </LegacyCard>
    )


    let cardContent = "Seamlessly enhance your web application security with Azure DevOps Boards. Create Azure Boards work items for api vulnerability issues and view them on the tap of a button"

    return (
        <IntegrationsLayout title= "Azure Boards" cardContent={cardContent} component={AzureBoardsCard} docsUrl="https://docs.akto.io/traffic-connections/postman"/> 
    )
}

export default AzureBoards