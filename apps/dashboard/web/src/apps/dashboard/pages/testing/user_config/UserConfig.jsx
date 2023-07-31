import { Button, Collapsible, Divider, LegacyCard, LegacyStack, Page, } from "@shopify/polaris"
import { ChevronRightMinor, ChevronDownMinor } from '@shopify/polaris-icons';
import { useState } from "react";
import api from "../api"
import { useEffect } from "react";
import HardCoded from "./HardCoded";
import SpinnerCentered from "../../../components/progress/SpinnerCentered";
import TestingStore from "../testingStore";
import Automated from "./Automated";
import Store from "../../../store";

function UserConfig() {

    const setToastConfig = Store(state => state.setToastConfig)
    const setAuthMechanism = TestingStore(state => state.setAuthMechanism)
    const [isLoading, setIsLoading] = useState(true)
    const [hardcodedOpen, setHardcodedOpen] = useState(true);

    const handleToggleHardcodedOpen = () => setHardcodedOpen((prev) => !prev)

    async function fetchAuthMechanismData() {
        setIsLoading(true)
        const authMechanismDataResponse = await api.fetchAuthMechanismData()
        console.log(authMechanismDataResponse)
        if (authMechanismDataResponse && authMechanismDataResponse.authMechanism) {
            const authMechanism = authMechanismDataResponse.authMechanism
            setAuthMechanism(authMechanism)
            if (authMechanism.type === "HARDCODED") setHardcodedOpen(true)
            else setHardcodedOpen(false)
        }
        setIsLoading(false)
    }

    useEffect(() => {
        fetchAuthMechanismData()
    }, [])

    async function handleStopAlltests() {
        await api.stopAllTests()
        setToastConfig({ isActive: true, isError: false, message: "All tests stopped!" })
    }

    const pageMarkup = (
        <Page
            title="User config"
            primaryAction={{ content: 'Stop all tests', onAction: handleStopAlltests }}
            divider
            fullWidth
        >
            <LegacyCard sectioned title="Choose auth token configuration">
                <Divider />
                <LegacyCard.Section>
                    <LegacyStack vertical>
                        <Button
                            onClick={handleToggleHardcodedOpen}
                            ariaExpanded={hardcodedOpen}
                            icon={hardcodedOpen ? ChevronDownMinor : ChevronRightMinor}
                            ariaControls="hardcoded"
                        >
                            Hard coded
                        </Button>
                        <Collapsible
                            open={hardcodedOpen}
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
                            onClick={handleToggleHardcodedOpen}
                            ariaExpanded={!hardcodedOpen}
                            icon={!hardcodedOpen ? ChevronDownMinor : ChevronRightMinor}
                            ariaControls="automated"
                        >
                            Automated
                        </Button>
                        <Collapsible
                            open={!hardcodedOpen}
                            id="automated"
                            transition={{ duration: '500ms', timingFunction: 'ease-in-out' }}
                            expandOnPrint
                        >
                            <Automated /> 
                        </Collapsible>
                    </LegacyStack>
                </LegacyCard.Section>

            </LegacyCard>
        </Page>
    )

    return (
        isLoading ? 
            <SpinnerCentered /> :
            <div>
             {pageMarkup}
            </div>
    )
}

export default UserConfig