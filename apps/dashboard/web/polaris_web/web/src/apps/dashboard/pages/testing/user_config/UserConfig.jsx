import AktoButton from './../../../components/shared/AktoButton';
import { Box, Button, Collapsible, Divider, LegacyCard, LegacyStack, Text } from "@shopify/polaris"
import { ChevronRightMinor, ChevronDownMinor } from '@shopify/polaris-icons';
import { useState } from "react";
import api from "../api"
import { useEffect } from "react";
import HardCoded from "./HardCoded";
import SpinnerCentered from "../../../components/progress/SpinnerCentered";
import TestingStore from "../testingStore";
import Automated from "./Automated";
import Store from "../../../store";
import PageWithMultipleCards from "../../../components/layouts/PageWithMultipleCards"
import Dropdown from "../../../components/layouts/Dropdown";
import settingRequests from "../../settings/api";
import TestCollectionConfiguration from '../configurations/TestCollectionConfiguration'
import PersistStore from '../../../../main/PersistStore';

function UserConfig() {
    const userRole = PersistStore(state => state.userRole)

    const setToastConfig = Store(state => state.setToastConfig)
    const setAuthMechanism = TestingStore(state => state.setAuthMechanism)
    const [isLoading, setIsLoading] = useState(true)
    const [hardcodedOpen, setHardcodedOpen] = useState(true);
    const [initialLimit, setInitialLimit] = useState(0);

    const handleToggleHardcodedOpen = () => setHardcodedOpen((prev) => !prev)

    async function fetchAuthMechanismData() {
        setIsLoading(true)
        const authMechanismDataResponse = await api.fetchAuthMechanismData()
        if (authMechanismDataResponse && authMechanismDataResponse.authMechanism) {
            const authMechanism = authMechanismDataResponse.authMechanism
            setAuthMechanism(authMechanism)
            if (authMechanism.type === "HARDCODED") setHardcodedOpen(true)
            else setHardcodedOpen(false)
        }

        await settingRequests.fetchAdminSettings().then((resp)=> {
            setInitialLimit(resp.accountSettings.globalRateLimit);
        })
        setIsLoading(false)
    }

    useEffect(() => {
        fetchAuthMechanismData()
    }, [])

    async function handleStopAllTests() {
        await api.stopAllTests()
        setToastConfig({ isActive: true, isError: false, message: "All tests stopped!" })
    }

    const requestPerMinValues = [0, 10, 20, 30, 60, 80, 100, 200, 300, 400, 600, 1000]
    const dropdownItems = requestPerMinValues.map((x)=> {
        return{
            label : x === 0 ? "No limit" : x.toString(),
            value: x
        }
    })

    const handleSelect = async(limit) => {
        if(userRole === 'GUEST') {
            setToastConfig({ isActive: true, isError: true, message: `You don't have permissions` })
            return
        }
        
        setInitialLimit(limit)
        await api.updateGlobalRateLimit(limit)
        setToastConfig({ isActive: true, isError: false, message: `Global rate limit set successfully` })
    }

    const authTokenComponent = (
        <LegacyCard sectioned title="Choose auth token configuration" key="bodyComponent">
            <Divider />
            <LegacyCard.Section>
                <LegacyStack vertical>
                    <AktoButton
                        id={"hardcoded-token-expand-button"}
                        onClick={handleToggleHardcodedOpen}
                        ariaExpanded={hardcodedOpen}
                        icon={hardcodedOpen ? ChevronDownMinor : ChevronRightMinor}
                        ariaControls="hardcoded"
                    >
                        Hard coded
                    </AktoButton>
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
                    <AktoButton
                        id={"automated-token-expand-button"}
                        onClick={handleToggleHardcodedOpen}
                        ariaExpanded={!hardcodedOpen}
                        icon={!hardcodedOpen ? ChevronDownMinor : ChevronRightMinor}
                        ariaControls="automated"
                    >
                        Automated
                    </AktoButton>
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
    )

    const rateLimit = (
        <LegacyCard sectioned title="Configure global rate limit" key="globalRateLimit">
            <Divider />
            <LegacyCard.Section>
                <div style={{ display: "grid", gridTemplateColumns: "max-content max-content", gap: "10px", alignItems: "center" }}>
                    <Text>Allowed requests / min:</Text>
                    <Dropdown
                        selected={handleSelect}
                        menuItems={dropdownItems}
                        initial={initialLimit}
                    />

                </div>
            </LegacyCard.Section>

            
        </LegacyCard>
    )

    const components = [<TestCollectionConfiguration/>, rateLimit]

    return (
        isLoading ? <SpinnerCentered /> 
           :<PageWithMultipleCards 
                components={components}
                isFirstPage={true}
                divider={true}
                title ={
                    <Text variant="headingLg">
                        User config
                    </Text>
                }
                primaryAction={{ content: 'Stop all tests', onAction: handleStopAllTests, 'disabled': (userRole === 'GUEST') }}
            />

    )
}

export default UserConfig