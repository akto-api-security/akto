import React, { useEffect, useState } from 'react'
import settingFunctions from '../module'
import IntegrationsLayout from './IntegrationsLayout'
import { Divider, LegacyCard, Text, TextField, VerticalStack } from '@shopify/polaris'
import PasswordTextField from '../../../components/layouts/PasswordTextField'
import func from "@/util/func"

const AzureBoards = () => {
    
    const [organization, setOrganization] = useState('')
    const [projectIds, setProjectIds] = useState('')
    const [personalAuthToken, setPersonalAuthToken] = useState('')
    const [collapsibleOpen, setCollapsibleOpen] = useState(false)

    async function fetchAzureBoardsInteg() {
        let azureBoardsInteg = await settingFunctions.fetchAzureBoardsIntegration()
        setOrganization(azureBoardsInteg != null ? azureBoardsInteg.organization : '')
        setProjectIds(azureBoardsInteg != null ? azureBoardsInteg.projectList?.join(',') : '')
        setPersonalAuthToken(azureBoardsInteg != null ? azureBoardsInteg.personalAuthToken : '')
    }

    useEffect(() => {
        fetchAzureBoardsInteg()
    }, [])

    async function addAzureBoardsIntegration(){
        await settingFunctions.addAzureBoardsIntegration(organization, projectIds.split(','), personalAuthToken)
        func.setToast(true, false, "Successfully added Azure Boards Integration")
        fetchAzureBoardsInteg()
    }
    
    const AzureBoardsCard = (
        <LegacyCard
            primaryFooterAction={{content: 'Save', onAction: addAzureBoardsIntegration }}
        >
          <LegacyCard.Section>
            <Text variant="headingMd">Integrate Azure Boards</Text>
          </LegacyCard.Section>

          <LegacyCard.Section>
                <VerticalStack gap={"2"}>
                    <TextField label="Organization" value={organization} helpText="Specify the organization name" placeholder='Organization name' requiredIndicator onChange={setOrganization} />
                    <PasswordTextField label="Personal Auth Token" helpText="Specify the personal auth token created for your project" field={personalAuthToken} onFunc={true} setField={setPersonalAuthToken} />
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