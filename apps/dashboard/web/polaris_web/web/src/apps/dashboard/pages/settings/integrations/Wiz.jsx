import { Divider, LegacyCard, Text, TextField, VerticalStack } from "@shopify/polaris"
import IntegrationsLayout from "./IntegrationsLayout"
import PasswordTextField from "@/apps/dashboard/components/layouts/PasswordTextField"
import { useEffect, useState } from "react"
import settingFunctions from "../module"
import func from "@/util/func"

function Wiz() {

    const [tenantDataCenter, setTenantDataCenter] = useState('')
    const [clientId, setClientId] = useState('')
    const [clientSecret, setClientSecret] = useState('')
    const [isSaving, setIsSaving] = useState(false)
    const [isRemoveable, setIsRemoveable] = useState(false)

    const resetFields = () => { 
        setTenantDataCenter('')
        setClientId('') 
        setClientSecret('') 
        setIsRemoveable(false) 
    }

    async function fetchWizIntegration() {

        try {
            let wizIntegration = await settingFunctions.fetchWizIntegration()

            // Check if integration exists (has tenantDataCenter and clientId)
            const hasIntegration = wizIntegration && wizIntegration.tenantDataCenter && wizIntegration.clientId

            if (hasIntegration) {
                setTenantDataCenter(wizIntegration.tenantDataCenter || '')
                setClientId(wizIntegration.clientId || '')
                setClientSecret(null) // Don't show existing secret
                setIsRemoveable(true)    
            } else {
                // No integration exists - reset everything
                resetFields()
            }
        } catch (error) {
            func.setToast(true, true, 'Error fetching Wiz integration:' + error)
            // Reset on error
            resetFields()
        }
    }

    useEffect(() => {
        fetchWizIntegration()
    }, [])

    async function addWizIntegration(){
        if (!tenantDataCenter?.trim() || !clientId?.trim() || !clientSecret?.trim()) {
            func.setToast(true, true, "Please fill all required fields")
            return
        }

        setIsSaving(true)
        try {
            await settingFunctions.addWizIntegration(tenantDataCenter, clientId, clientSecret)
            func.setToast(true, false, "Successfully added Wiz Integration")
            fetchWizIntegration()
        } catch (error) {
            func.setToast(true, true, "Failed to add Wiz Integration")
        } finally {
            setIsSaving(false)
        }
    }

    async function removeWizIntegration() {
        setIsSaving(true)
        try {
            await settingFunctions.removeWizIntegration()
            func.setToast(true, false, "Successfully removed Wiz Integration")
            resetFields()
        } catch (error) {
            func.setToast(true, true, "Failed to remove Wiz Integration")
        } finally {
            setIsSaving(false)
        }
    }

    const isSaveDisabled = () => {
        return isSaving || !tenantDataCenter?.trim() || !clientId?.trim() ||
               (clientSecret === null ? true : !clientSecret?.trim())
    }

    const cardContent = "Seamlessly enhance your web application security with Akto's Wiz integration. Enrich your wiz dashboard with API vulnerabilities discovered by Akto."

    const wizCard = (
        <LegacyCard
            primaryFooterAction={{
                content: isSaving ? 'Saving...' : 'Save',
                onAction: addWizIntegration,
                disabled: isSaveDisabled(),
                loading: isSaving
            }}
            secondaryFooterActions={[{
                content: 'Remove',
                onAction: removeWizIntegration,
                disabled: !isRemoveable || isSaving
            }]}
        >
            <LegacyCard.Section>
                <Text variant="headingMd">Integrate Wiz</Text>
            </LegacyCard.Section>


            <LegacyCard.Section>
                <VerticalStack gap={"4"}>
                    <TextField
                        label="Tenant Data Center"
                        value={tenantDataCenter}
                        helpText="Specify your Wiz regional tenant data center (e.g., us1, us2, eu1, eu2)"
                        placeholder='Tenant Data Center'
                        requiredIndicator
                        onChange={setTenantDataCenter}
                    />
                    <TextField
                        label="Client ID"
                        value={clientId}
                        helpText="Specify the OAuth client ID of your Wiz service account"
                        placeholder='Client ID'
                        requiredIndicator
                        onChange={setClientId}
                    />
                    <PasswordTextField
                        label="Client Secret"
                        helpText="Specify the OAuth client Secret of your Wiz service account"
                        field={clientSecret === null ? '' : clientSecret}
                        onFunc={true}
                        setField={setClientSecret}
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
            title="Wiz"
            cardContent={cardContent}
            component={wizCard}
            docsUrl="https://docs.akto.io/integrations/wiz"
        />
    )
}

export default Wiz