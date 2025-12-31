import React, { useEffect, useState } from 'react'
import settingFunctions from '../module'
import IntegrationsLayout from './IntegrationsLayout'
import { Button, Divider, LegacyCard, Text, TextField, VerticalStack } from '@shopify/polaris'
import PasswordTextField from '../../../components/layouts/PasswordTextField'
import DropdownSearch from '../../../components/shared/DropdownSearch'
import func from "@/util/func"

const DevRev = () => {

    const [orgUrl, setOrgUrl] = useState('')
    const [personalAccessToken, setPersonalAccessToken] = useState('')
    const [parts, setParts] = useState([])
    const [selectedParts, setSelectedParts] = useState([])
    const [isRemoveable, setIsRemoveable] = useState(false)
    const [isSaving, setIsSaving] = useState(false)
    const [isFetchingParts, setIsFetchingParts] = useState(false)
    const [partsFetched, setPartsFetched] = useState(false)

    async function fetchDevRevInteg() {
        try {
            let devRevInteg = await settingFunctions.fetchDevRevIntegration()

            const hasIntegration = devRevInteg && devRevInteg.orgUrl

            if (hasIntegration) {
                setOrgUrl(devRevInteg.orgUrl || '')
                setPersonalAccessToken('')

                const partsMap = devRevInteg.partsMap || {}
                const partIds = Object.keys(partsMap)
                setSelectedParts(partIds)
                setIsRemoveable(true)

                if (partIds.length > 0) {
                    const selectedPartOptions = partIds.map(id => ({
                        label: partsMap[id],
                        value: id
                    }))
                    setParts(selectedPartOptions)
                    setPartsFetched(true)
                }
            } else {
                setOrgUrl('')
                setPersonalAccessToken('')
                setSelectedParts([])
                setIsRemoveable(false)
                setPartsFetched(false)
            }
        } catch (error) {
            func.setToast(true, true, 'Error fetching DevRev integration: ' + error)
            setOrgUrl('')
            setPersonalAccessToken('')
            setSelectedParts([])
            setIsRemoveable(false)
            setPartsFetched(false)
        }
    }

    async function fetchDevRevParts(token) {
        if (!token?.trim() && !isRemoveable) {
            func.setToast(true, true, "Please enter Personal Access Token first")
            return
        }

        setIsFetchingParts(true)
        try {
            const partsMap = await settingFunctions.fetchDevRevParts(token || null)

            const formattedParts = Object.entries(partsMap || {}).map(([id, name]) => ({
                label: name,
                value: id
            }))
            setParts(formattedParts)
            setPartsFetched(true)

            if (formattedParts.length === 0) {
                func.setToast(true, true, "No parts found. Please check your credentials.")
                setSelectedParts([])
            } else {
                func.setToast(true, false, `Successfully fetched ${formattedParts.length} parts`)
            }
        } catch (error) {
            const errorMessage = error?.response?.data?.actionErrors?.[0] || "Failed to fetch DevRev parts. Please verify your credentials."
            func.setToast(true, true, errorMessage)
        } finally {
            setIsFetchingParts(false)
        }
    }

    const handleFetchParts = () => {
        fetchDevRevParts(personalAccessToken)
    }

    useEffect(() => {
        fetchDevRevInteg()
    }, [])

    async function addDevRevIntegration(){
        if (!orgUrl?.trim() || selectedParts.length === 0) {
            func.setToast(true, true, "Please fill all required fields and select at least one part")
            return
        }

        if (!func.validateUrl(orgUrl.trim())) {
            func.setToast(true, true, "Please enter a valid organization URL (must be a valid http or https URL)")
            return
        }

        if (!isRemoveable && !personalAccessToken?.trim()) {
            func.setToast(true, true, "Please enter Personal Access Token")
            return
        }

        const partsMap = {}
        selectedParts.forEach(partId => {
            const part = parts.find(p => p.value === partId)
            if (part) {
                partsMap[partId] = part.label
            }
        })

        setIsSaving(true)
        try {
            const tokenToSend = personalAccessToken?.trim() || null
            await settingFunctions.addDevRevIntegration(orgUrl, tokenToSend, partsMap)

            const message = isRemoveable ? "Successfully updated DevRev Integration" : "Successfully added DevRev Integration"
            func.setToast(true, false, message)

            // Reload the page after successful save/update
            window.location.reload()
        } catch (error) {
            const errorMessage = error?.response?.data?.actionErrors?.[0] ||
                (isRemoveable ? "Failed to update DevRev Integration" : "Failed to add DevRev Integration")
            func.setToast(true, true, errorMessage)
        } finally {
            setIsSaving(false)
        }
    }

    async function removeDevRevIntegration() {
        setIsSaving(true)
        try {
            await settingFunctions.removeDevRevIntegration()
            func.setToast(true, false, "Successfully removed DevRev Integration")
            setOrgUrl('')
            setPersonalAccessToken('')
            setSelectedParts([])
            setParts([])
            setPartsFetched(false)
            setIsRemoveable(false)

            // Reload the page after successful removal
            window.location.reload()
        } catch (error) {
            const errorMessage = error?.response?.data?.actionErrors?.[0] || "Failed to remove DevRev Integration"
            func.setToast(true, true, errorMessage)
        } finally {
            setIsSaving(false)
        }
    }

    const isSaveDisabled = () => {
        const tokenRequired = !isRemoveable && !personalAccessToken?.trim()
        return isSaving || !orgUrl?.trim() || tokenRequired || selectedParts.length === 0
    }

    const isFetchButtonDisabled = () => {
        return isFetchingParts || (!isRemoveable && !personalAccessToken?.trim())
    }

    const DevRevCard = (
        <LegacyCard
            primaryFooterAction={{
                content: isSaving ? (isRemoveable ? 'Updating...' : 'Saving...') : (isRemoveable ? 'Update' : 'Save'),
                onAction: addDevRevIntegration,
                disabled: isSaveDisabled(),
                loading: isSaving
            }}
            secondaryFooterActions={[{
                content: 'Remove',
                onAction: removeDevRevIntegration,
                disabled: !isRemoveable || isSaving
            }]}
        >
          <LegacyCard.Section>
            <Text variant="headingMd">Integrate DevRev</Text>
          </LegacyCard.Section>

          <LegacyCard.Section>
                <VerticalStack gap={"4"}>
                    <TextField
                        label="Organization URL"
                        value={orgUrl}
                        helpText="Specify your DevRev organization URL (e.g., https://app.devrev.ai/<your-org-slug>)"
                        placeholder='Organization URL'
                        requiredIndicator
                        onChange={setOrgUrl}
                    />
                    <PasswordTextField
                        label="Personal Access Token"
                        helpText={isRemoveable
                            ? "Leave blank to keep your existing token, or enter a new token to update it"
                            : "Specify your DevRev personal access token from your DevRev account settings"}
                        field={personalAccessToken}
                        onFunc={true}
                        setField={setPersonalAccessToken}
                        placeholder={isRemoveable ? "Leave blank to keep existing token" : "Personal Access Token"}
                        requiredIndicator={!isRemoveable}
                    />

                    <Button
                        onClick={handleFetchParts}
                        disabled={isFetchButtonDisabled()}
                        loading={isFetchingParts}
                    >
                        {isFetchingParts ? 'Fetching Parts...' : 'Fetch Parts'}
                    </Button>

                    {partsFetched && parts.length > 0 && (
                        <DropdownSearch
                            id="devrev-part-select"
                            label="DevRev Parts"
                            placeholder={selectedParts.length > 0 ? "Search to add more parts" : "Search and select parts"}
                            optionsList={parts}
                            setSelected={setSelectedParts}
                            value=""
                            preSelected={selectedParts}
                            allowMultiple={true}
                            itemName="part"
                            textfieldRequiredIndicator={true}
                        />
                    )}
                </VerticalStack>
          </LegacyCard.Section>
          <Divider />
          <br/>
        </LegacyCard>
    )

    let cardContent = "Seamlessly enhance your API security workflow with DevRev integration. Create DevRev issues for API vulnerability findings and manage them directly from your DevRev workspace"

    return (
        <IntegrationsLayout
            title="DevRev"
            cardContent={cardContent}
            component={DevRevCard}
            docsUrl="https://docs.akto.io/issues/how-to/devrev-integration"
        />
    )
}

export default DevRev
