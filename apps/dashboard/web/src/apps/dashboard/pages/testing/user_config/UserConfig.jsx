import { Button, Card, Collapsible, Icon, LegacyCard, LegacyStack, Link, Page, TextContainer, TextField } from "@shopify/polaris"
import { ChevronRightMinor } from '@shopify/polaris-icons';
import { useCallback, useState } from "react";
import api from "../api"
import Store from "../../../store";
import { useEffect } from "react";

function UserConfig() {

    const [open, setOpen] = useState(true);

    const handleToggle = useCallback(() => setOpen((open) => !open), []);

    async function fetchAuthMechanismData() {
        const authMechanismDataResponse = await api.fetchAuthMechanismData()
        if (authMechanismDataResponse
            && authMechanismDataResponse.authMechanism
            && authMechanismDataResponse.authMechanism.type === "HARDCODED") {
            const authParam = authMechanismDataResponse.authMechanism.authParams[0]
            setUserConfig({
                authHeaderKey: authParam.key,
                authHeaderValue: authParam.value
            })
        }
    }

    useEffect(() => {
        fetchAuthMechanismData()
    }, [])

    async function handleStopAlltests() {
        await api.stopAllTests()
        setToastConfig({ isActive: true, isError: false, message: "All tests stopped!" })
    }

    return (
        <Page
            title="User config"
            primaryAction={{ content: 'Stop all tests', onAction: handleStopAlltests }}
            divider
            fullWidth
        >
            {/* <LegacyCard title="Inject hard-coded attacker auth token">
                <LegacyCard.Section>
                    <TextField
                        label="Auth header key"
                        value={userConfig.authHeaderKey} placeholder='' onChange={(authHeaderKey) => updateUserConfig("authHeaderKey", authHeaderKey)} />
                    <br />
                    <TextField label="Auth header value" value={userConfig.authHeaderValue} placeholder='' onChange={(authHeaderValue) => updateUserConfig("authHeaderValue", authHeaderValue)} />
                    <br />
                    <Button
                        primary
                        disabled={!hasChanges}
                        onClick={handleSave}
                    >
                        Save changes
                    </Button>
                </LegacyCard.Section>
            </LegacyCard> */}

            <LegacyCard title="Choose Auth Token Configuration"></LegacyCard>
            <LegacyCard sectioned title="Choose auth token configuration">
                <LegacyCard.Section>
                <LegacyStack vertical>
                    <Button
                        onClick={handleToggle}
                        ariaExpanded={open}
                        icon={ChevronRightMinor}
                        ariaControls="basic-collapsible"
                    >
                        Inject hard-coded attacker auth token
                    </Button>
                    <Collapsible
                        open={open}
                        id="basic-collapsible"
                        transition={{ duration: '500ms', timingFunction: 'ease-in-out' }}
                        expandOnPrint
                    >
                        <TextContainer>
                            <p>
                                Your mailing list lets you contact customers or visitors who
                                have shown an interest in your store. Reach out to them with
                                exclusive offers or updates about your products.
                            </p>
                            <Link url="#">Test link</Link>
                        </TextContainer>
                    </Collapsible>
                </LegacyStack>
                </LegacyCard.Section>
             
            </LegacyCard>
        </Page>
    )
}

export default UserConfig