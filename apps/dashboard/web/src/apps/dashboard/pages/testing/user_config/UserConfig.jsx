import { Button, Card, Collapsible, Divider, Icon, LegacyCard, LegacyStack, Link, Page, TextContainer, TextField } from "@shopify/polaris"
import { ChevronRightMinor } from '@shopify/polaris-icons';
import { useCallback, useState } from "react";
import api from "../api"
import Store from "../../../store";
import { useEffect } from "react";
import HardCoded from "./HardCoded";

function UserConfig() {

    const [hardcodedOpen, setHardcodedOpen] = useState(true);

    const handleToggleHardcodedOpen = () => setHardcodedOpen((prev) => !prev)

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

            <LegacyCard sectioned title="Choose auth token configuration">
                <Divider />
                <LegacyCard.Section>
                    <LegacyStack vertical>
                        <Button
                            onClick={handleToggle}
                            ariaExpanded={open}
                            icon={ChevronRightMinor}
                            ariaControls="hardcoded"
                        >
                            Hard coded
                        </Button>
                        <Collapsible
                            open={open}
                            id="hardcoded"
                            transition={{ duration: '500ms', timingFunction: 'ease-in-out' }}
                            expandOnPrint
                        >
                            <HardCoded />
                        </Collapsible>
                    </LegacyStack>
                </LegacyCard.Section>


                <LegacyCard.Section>
                    <LegacyStack vertical>
                        <Button
                            onClick={handleToggle}
                            ariaExpanded={open}
                            icon={ChevronRightMinor}
                            ariaControls="automated"
                        >
                            Automated
                        </Button>
                        <Collapsible
                            open={false}
                            id="automated"
                            transition={{ duration: '500ms', timingFunction: 'ease-in-out' }}
                            expandOnPrint
                        >
                            <HardCoded />
                        </Collapsible>
                    </LegacyStack>
                </LegacyCard.Section>

            </LegacyCard>
        </Page>
    )
}

export default UserConfig