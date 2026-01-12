import React, { useEffect, useState } from 'react'
import settingFunctions from '../module'
import IntegrationsLayout from './IntegrationsLayout'
import { Divider, LegacyCard, Text, TextField, VerticalStack } from '@shopify/polaris'
import PasswordTextField from '../../../components/layouts/PasswordTextField'
import func from "@/util/func"

const AzureDataExplorer = () => {
    
    const [clusterEndpoint, setClusterEndpoint] = useState('')
    const [databaseName, setDatabaseName] = useState('')
    const [tenantId, setTenantId] = useState('')
    const [applicationClientId, setApplicationClientId] = useState('')
    const [applicationKey, setApplicationKey] = useState('')
    const [isRemoveable, setIsRemoveable] = useState(false)

    async function fetchAdxInteg() {
        let adxInteg = await settingFunctions.fetchAdxIntegration()
        setClusterEndpoint(adxInteg != null ? adxInteg.clusterEndpoint : '')
        setDatabaseName(adxInteg != null ? adxInteg.databaseName : '')
        setTenantId(adxInteg != null ? adxInteg.tenantId : '')
        setApplicationClientId(adxInteg != null ? adxInteg.applicationClientId : '')
        setApplicationKey(adxInteg != null ? null : '')
        setIsRemoveable(adxInteg != null ? true : false)
    }

    useEffect(() => {
        fetchAdxInteg()
    }, [])

    async function addAdxIntegration(){
        await settingFunctions.addAdxIntegration(
            clusterEndpoint, 
            databaseName, 
            tenantId, 
            applicationClientId, 
            applicationKey
        )
        func.setToast(true, false, "Successfully added Azure Data Explorer Integration")
        fetchAdxInteg()
    }

    async function removeAdxIntegration() {
        await settingFunctions.removeAdxIntegration()
        func.setToast(true, false, "Successfully removed Azure Data Explorer Integration")
        setClusterEndpoint('')
        setDatabaseName('')
        setTenantId('')
        setApplicationClientId('')
        setApplicationKey('')
        setIsRemoveable(false)
    }
    
    const AzureDataExplorerCard = (
        <LegacyCard
            primaryFooterAction={{content: 'Save', onAction: addAdxIntegration }}
            secondaryFooterActions={[{content: 'Remove', onAction: removeAdxIntegration, disabled: !isRemoveable}]}
        >
          <LegacyCard.Section>
            <Text variant="headingMd">Integrate Azure Data Explorer</Text>
          </LegacyCard.Section>

          <LegacyCard.Section>
                <VerticalStack gap={"2"}>
                    <TextField 
                        label="Cluster Endpoint" 
                        value={clusterEndpoint} 
                        helpText="Specify the Azure Data Explorer cluster endpoint (e.g. https://kvc-xxxxx.southcentralus.kusto.windows.net)" 
                        placeholder='Cluster Endpoint' 
                        requiredIndicator 
                        onChange={setClusterEndpoint} 
                    />
                    <TextField 
                        label="Database Name" 
                        value={databaseName} 
                        helpText="Specify the database name in your ADX cluster" 
                        placeholder='Database Name' 
                        requiredIndicator 
                        onChange={setDatabaseName} 
                    />
                    <TextField 
                        label="Tenant ID" 
                        value={tenantId} 
                        helpText="Specify your Azure AD tenant ID" 
                        placeholder='Tenant ID' 
                        requiredIndicator 
                        onChange={setTenantId} 
                    />
                    <TextField 
                        label="Application (Client) ID" 
                        value={applicationClientId} 
                        helpText="Specify the Azure AD application (client) ID" 
                        placeholder='Application Client ID' 
                        requiredIndicator 
                        onChange={setApplicationClientId} 
                    />
                    {applicationKey === null ? <></> : <PasswordTextField 
                        label="Application Key (Client Secret)" 
                        helpText="Specify the Azure AD application key (client secret)" 
                        field={applicationKey} 
                        onFunc={true} 
                        setField={setApplicationKey} 
                    />}
                </VerticalStack>
          </LegacyCard.Section> 
          <Divider />
          <br/>
        </LegacyCard>
    )

    let cardContent = "Export guardrail activity data to Azure Data Explorer (ADX) for advanced analytics and insights. Configure once and export data anytime with a single click."

    return (
        <IntegrationsLayout title="Azure Data Explorer" cardContent={cardContent} component={AzureDataExplorerCard} docsUrl="https://docs.akto.io/integrations/azure-data-explorer"/> 
    )
}

export default AzureDataExplorer

