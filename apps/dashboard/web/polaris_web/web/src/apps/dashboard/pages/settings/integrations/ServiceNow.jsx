import React, { useEffect, useState } from 'react'
import settingFunctions from '../module'
import IntegrationsLayout from './IntegrationsLayout'
import { Button, Divider, LegacyCard, Text, TextField, VerticalStack } from '@shopify/polaris'
import PasswordTextField from '../../../components/layouts/PasswordTextField'
import DropdownSearch from '../../../components/shared/DropdownSearch'
import func from "@/util/func"

const ServiceNow = () => {

    const [instanceUrl, setInstanceUrl] = useState('')
    const [clientId, setClientId] = useState('')
    const [clientSecret, setClientSecret] = useState('')
    const [tables, setTables] = useState([])
    const [selectedTables, setSelectedTables] = useState([])
    const [isRemoveable, setIsRemoveable] = useState(false)
    const [isSaving, setIsSaving] = useState(false)
    const [isFetchingTables, setIsFetchingTables] = useState(false)
    const [tablesFetched, setTablesFetched] = useState(false)

    async function fetchServiceNowInteg() {
        try {
            let serviceNowInteg = await settingFunctions.fetchServiceNowIntegration()

            // Check if integration exists (has instanceUrl and clientId)
            const hasIntegration = serviceNowInteg && serviceNowInteg.instanceUrl && serviceNowInteg.clientId

            if (hasIntegration) {
                setInstanceUrl(serviceNowInteg.instanceUrl || '')
                setClientId(serviceNowInteg.clientId || '')
                setClientSecret(null) // Don't show existing secret

                const tableNames = serviceNowInteg.tableNames || []
                setSelectedTables(tableNames)
                setIsRemoveable(true)

                // Create table options from selected tables only (for display purposes)
                // User needs to click "Fetch Tables" to see all available tables and modify
                if (tableNames.length > 0) {
                    const selectedTableOptions = tableNames.map(name => ({
                        label: name,
                        value: name
                    }))
                    setTables(selectedTableOptions)
                    setTablesFetched(true)
                }
            } else {
                // No integration exists - reset everything
                setInstanceUrl('')
                setClientId('')
                setClientSecret('')
                setSelectedTables([])
                setIsRemoveable(false)
                setTablesFetched(false)
            }
        } catch (error) {
            func.setToast(true, true, 'Error fetching ServiceNow integration:' + error)
            // Reset on error
            setInstanceUrl('')
            setClientId('')
            setClientSecret('')
            setSelectedTables([])
            setIsRemoveable(false)
            setTablesFetched(false)
        }
    }

    async function fetchServiceNowTables(url, id, secret) {
        if (!url?.trim() || !id?.trim()) {
            func.setToast(true, true, "Please enter Instance URL and Client ID first")
            return
        }

        if (secret !== null && !secret?.trim()) {
            func.setToast(true, true, "Please enter Client Secret")
            return
        }

        setIsFetchingTables(true)
        try {
            const tableList = await settingFunctions.fetchServiceNowTables(url, id, secret)

            const formattedTables = (tableList || []).map(table => ({
                label: table.label || table.name,
                value: table.name
            }))
            setTables(formattedTables)
            setTablesFetched(true)

            if (formattedTables.length === 0) {
                func.setToast(true, true, "No tables found. Please check your credentials.")
                setSelectedTables([])
            } else {
                func.setToast(true, false, `Successfully fetched ${formattedTables.length} tables`)
            }
        } catch (error) {
            func.setToast(true, true, "Failed to fetch ServiceNow tables. Please verify your credentials.")
            setTables([])
            setSelectedTables([])
            setTablesFetched(false)
        } finally {
            setIsFetchingTables(false)
        }
    }

    const handleFetchTables = () => {
        fetchServiceNowTables(instanceUrl, clientId, clientSecret)
    }

    useEffect(() => {
        fetchServiceNowInteg()
    }, [])

    async function addServiceNowIntegration(){
        if (!instanceUrl?.trim() || !clientId?.trim() || !clientSecret?.trim() || selectedTables.length === 0) {
            func.setToast(true, true, "Please fill all required fields and select at least one table")
            return
        }

        setIsSaving(true)
        try {
            await settingFunctions.addServiceNowIntegration(instanceUrl, clientId, clientSecret, selectedTables)
            func.setToast(true, false, "Successfully added ServiceNow Integration")
            fetchServiceNowInteg()
        } catch (error) {
            func.setToast(true, true, "Failed to add ServiceNow Integration")
        } finally {
            setIsSaving(false)
        }
    }

    async function removeServiceNowIntegration() {
        setIsSaving(true)
        try {
            await settingFunctions.removeServiceNowIntegration()
            func.setToast(true, false, "Successfully removed ServiceNow Integration")
            setInstanceUrl('')
            setClientId('')
            setClientSecret('')
            setSelectedTables([])
            setTables([])
            setTablesFetched(false)
            setIsRemoveable(false)
        } catch (error) {
            func.setToast(true, true, "Failed to remove ServiceNow Integration")
        } finally {
            setIsSaving(false)
        }
    }

    const isSaveDisabled = () => {
        return isSaving || !instanceUrl?.trim() || !clientId?.trim() ||
               (clientSecret === null ? false : !clientSecret?.trim()) ||
               selectedTables.length === 0
    }

    const isFetchButtonDisabled = () => {
        return isFetchingTables || !instanceUrl?.trim() || !clientId?.trim() || !clientSecret?.trim()
    }

    const ServiceNowCard = (
        <LegacyCard
            primaryFooterAction={{
                content: isSaving ? 'Saving...' : 'Save',
                onAction: addServiceNowIntegration,
                disabled: isSaveDisabled(),
                loading: isSaving
            }}
            secondaryFooterActions={[{
                content: 'Remove',
                onAction: removeServiceNowIntegration,
                disabled: !isRemoveable || isSaving
            }]}
        >
          <LegacyCard.Section>
            <Text variant="headingMd">Integrate ServiceNow</Text>
          </LegacyCard.Section>

          <LegacyCard.Section>
                <VerticalStack gap={"4"}>
                    <TextField
                        label="Instance URL"
                        value={instanceUrl}
                        helpText="Specify your ServiceNow instance URL (e.g., https://your-instance.service-now.com)"
                        placeholder='Instance URL'
                        requiredIndicator
                        onChange={setInstanceUrl}
                    />
                    <TextField
                        label="Client ID"
                        value={clientId}
                        helpText="Specify the OAuth client ID from your ServiceNow application registry"
                        placeholder='Client ID'
                        requiredIndicator
                        onChange={setClientId}
                    />
                    <PasswordTextField
                        label="Client Secret"
                        helpText="Specify the OAuth client secret from your ServiceNow application registry"
                        field={clientSecret === null ? '' : clientSecret}
                        onFunc={true}
                        setField={setClientSecret}
                        requiredIndicator
                    />

                    <Button
                        onClick={handleFetchTables}
                        disabled={isFetchButtonDisabled()}
                        loading={isFetchingTables}
                    >
                        {isFetchingTables ? 'Fetching Tables...' : 'Fetch Tables'}
                    </Button>

                    {tablesFetched && tables.length > 0 && (
                        <DropdownSearch
                            id="servicenow-table-select"
                            label="ServiceNow Tables"
                            placeholder={selectedTables.length > 0 ? "Search to add more tables" : "Search and select tables"}
                            optionsList={tables}
                            setSelected={setSelectedTables}
                            value=""
                            preSelected={selectedTables}
                            allowMultiple={true}
                            itemName="table"
                            textfieldRequiredIndicator={true}
                        />
                    )}
                </VerticalStack>
          </LegacyCard.Section>
          <Divider />
          <br/>
        </LegacyCard>
    )

    let cardContent = "Seamlessly enhance your web application security with ServiceNow integration. Create ServiceNow incidents for API vulnerability issues and view them on the tap of a button"

    return (
        <IntegrationsLayout
            title="ServiceNow"
            cardContent={cardContent}
            component={ServiceNowCard}
            docsUrl="https://docs.akto.io/traffic-connections/postman"
        />
    )
}

export default ServiceNow
