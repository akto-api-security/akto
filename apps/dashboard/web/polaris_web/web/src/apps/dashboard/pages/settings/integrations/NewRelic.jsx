import { Divider, LegacyCard, Text, TextField, VerticalStack } from "@shopify/polaris"
import IntegrationsLayout from "./IntegrationsLayout"
import PasswordTextField from "@/apps/dashboard/components/layouts/PasswordTextField"
import { useEffect, useState } from "react"
import settingFunctions from "../module"
import func from "@/util/func"

function NewRelic() {
    
    const [apiKey, setApiKey] = useState('')
    const [isSaving, setIsSaving] = useState(false)
    const [isRemoveable, setIsRemoveable] = useState(false)

    const resetFields = () => {
        setApiKey('')
        setIsRemoveable(false)
    }

    async function fetchNewRelicIntegration() {

        try {
            let newRelicIntegration = await settingFunctions.fetchNewRelicIntegration()

            if (newRelicIntegration) {
                setApiKey(null) // Don't show existing secret
                setIsRemoveable(true)    
            } else {
                // No integration exists - reset everything
                resetFields()
            }
        } catch (error) {
            func.setToast(true, true, 'Error fetching New Relic integration:' + error)
            // Reset on error
            resetFields()
        }
    }

    useEffect(() => {
        fetchNewRelicIntegration()
    }, [])

    async function addNewRelicIntegration(){
        if (!apiKey?.trim()) {
            func.setToast(true, true, "Please fill all required fields")
            return
        }

        setIsSaving(true)
        try {
            await settingFunctions.addNewRelicIntegration(apiKey)
            func.setToast(true, false, "Successfully added New Relic Integration")
            fetchNewRelicIntegration()
        } catch (error) {
            func.setToast(true, true, error?.response?.data?.actionErrors?.[0] || "Failed to add New Relic Integration")
        } finally {
            setIsSaving(false)
        }
    }

    async function removeNewRelicIntegration() {
        setIsSaving(true)
        try {
            await settingFunctions.removeNewRelicIntegration()
            func.setToast(true, false, "Successfully removed New Relic Integration")
            resetFields()
        } catch (error) {
            func.setToast(true, true, "Failed to remove New Relic Integration")
        } finally {
            setIsSaving(false)
        }
    }

    const isSaveDisabled = () => {
        return isSaving || (apiKey === null ? true : !apiKey?.trim())
    }

    const cardContent = "Send metrics and heartbeat events from Akto's hybrid modules to your New Relic dashboard."

    const newRelicCard = (
        <LegacyCard
            primaryFooterAction={{
                content: isSaving ? 'Saving...' : 'Save',
                onAction: addNewRelicIntegration,
                disabled: isSaveDisabled(),
                loading: isSaving
            }}
            secondaryFooterActions={[{
                content: 'Remove',
                onAction: removeNewRelicIntegration,
                disabled: !isRemoveable || isSaving
            }]}
        >
            <LegacyCard.Section>
                <Text variant="headingMd">Integrate New Relic</Text>
            </LegacyCard.Section>


            <LegacyCard.Section>
                <VerticalStack gap={"4"}>
                    <PasswordTextField
                        label="API Key"
                        helpText="Specify the API Key for your New Relic account."
                        field={apiKey === null ? '' : apiKey}
                        onFunc={true}
                        setField={setApiKey}
                        requiredIndicator
                    />
                </VerticalStack>
            </LegacyCard.Section>
            <Divider />
            <br/>
        </LegacyCard>
    )

    return (
        <IntegrationsLayout
            title="New Relic"
            cardContent={cardContent}
            component={newRelicCard}
            docsUrl="https://docs.akto.io/integrations/new-relic"
        />
    )
}

export default NewRelic