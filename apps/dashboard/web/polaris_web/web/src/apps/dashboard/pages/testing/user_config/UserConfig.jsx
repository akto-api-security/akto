import { TextField, Button, Collapsible, Divider, LegacyCard, LegacyStack, Text, Box } from "@shopify/polaris"
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
import InfoCard from "../../dashboard/new_components/InfoCard";
import LocalStore from "../../../../main/LocalStorageStore";
import func from "@/util/func"
import SampleData from "../../../components/shared/SampleData";

function UserConfig() {

    const setToastConfig = Store(state => state.setToastConfig)
    const setAuthMechanism = TestingStore(state => state.setAuthMechanism)
    const [isLoading, setIsLoading] = useState(true)
    const [hardcodedOpen, setHardcodedOpen] = useState(true);
    const [initialLimit, setInitialLimit] = useState(0);
    const [preRequestScript, setPreRequestScript] = useState({javascript: ""});
    const [commonTestTemplate, setCommonTestTemplate] = useState({message: ""});
    const [commonTestTemplate2, setCommonTestTemplate2] = useState("");
    const [initialDeltaTime, setInitialDeltaTime] = useState(120) ;

    const handleToggleHardcodedOpen = () => setHardcodedOpen((prev) => !prev)

    const handlePreRequestScriptChange = (value) => { 
        setPreRequestScript({...preRequestScript, javascript: value})
    }

    async function fetchAuthMechanismData() {
        setIsLoading(true)
        const authMechanismDataResponse = await api.fetchAuthMechanismData()
        if (authMechanismDataResponse && authMechanismDataResponse.authMechanism) {
            const authMechanism = authMechanismDataResponse.authMechanism
            setAuthMechanism(authMechanism)
            if (authMechanism.type === "HARDCODED") setHardcodedOpen(true)
            else setHardcodedOpen(false)
        }

        if(window.USER_ROLE === 'ADMIN') {
            await settingRequests.fetchAdminSettings().then((resp)=> {
                setInitialLimit(resp.accountSettings.globalRateLimit);
                const val = resp?.accountSettings?.timeForScheduledSummaries === undefined || resp?.accountSettings?.timeForScheduledSummaries === 0 ? (120*60) : resp?.accountSettings?.timeForScheduledSummaries
                setInitialDeltaTime(val/60)
                LocalStore.getState().setDefaultIgnoreSummaryTime(val)
            })
        }
        try {
            await api.fetchScript().then((resp)=> {
                if (resp && resp.testScript) { 
                    setPreRequestScript(resp.testScript)
                }
            });
        } catch(e){
        }

        setIsLoading(false)
    }

    async function fetchCommonTestTemplate() {
        const resp = await api.fetchCommonTestTemplate()
        if (resp) {
            setCommonTestTemplate({ message: resp })
        }
    }

    useEffect(() => {
        fetchAuthMechanismData()
        fetchCommonTestTemplate()
    }, [])

    async function addOrUpdateScript() {
        if (preRequestScript.id) {
            api.updateScript(preRequestScript.id, preRequestScript.javascript)
            func.setToast(true, false, "Pre-request script updated")
        } else {
            api.addScript(preRequestScript)
            func.setToast(true, false, "Pre-request script added")
        }
    }

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

    const optionsForDeltaTime = [
        {label: '10 minutes', value: 10},
        {label: '20 minutes', value: 20},
        {label: '30 minutes', value: 30},
        {label: '45 minutes', value: 45},
        {label: '1 hour', value: 60},
        {label: '2 hours', value: 120},
        {label: '4 hours', value: 240},
    ]

    const handleSelect = async(limit) => {
        setInitialLimit(limit)
        await api.updateGlobalRateLimit(limit)
        setToastConfig({ isActive: true, isError: false, message: `Global rate limit set successfully` })
    }

    const handleUpdateDeltaTime = async(limit) => {
        setInitialDeltaTime(limit);
        LocalStore.getState().setDefaultIgnoreSummaryTime(limit * 60)
        await api.updateDeltaTimeForSummaries(limit * 60);
        setToastConfig({ isActive: true, isError: false, message: `Ignore time updated successfully` })
    }

    const authTokenComponent = (
        <LegacyCard sectioned title="Choose auth token configuration" key="bodyComponent">
            <Divider />
            <LegacyCard.Section>
                <LegacyStack vertical>
                    <Button
                        id={"hardcoded-token-expand-button"}
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
                        id={"automated-token-expand-button"}
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
    )

    const rateLimit = (
        <LegacyCard sectioned title="Configure global rate limit" key="globalRateLimit">
            <Divider />
            <LegacyCard.Section>
                <div style={{ display: "grid", gridTemplateColumns: "max-content max-content", gap: "10px", alignItems: "center" }}>
                    <Dropdown
                        selected={handleSelect}
                        menuItems={dropdownItems}
                        initial={initialLimit}
                    />

                </div>
            </LegacyCard.Section>

            
        </LegacyCard>
    )

    const updateDeltaPeriodTime = (
        <InfoCard
            key={"summaryTime"}
            title={"Update ignore time for testing"}
            titleToolTip={"User can update the default time for ignoring a test run pick up time if queued up"}
            component={
                <Box maxWidth="200px">
                    <Dropdown
                        selected={handleUpdateDeltaTime}
                        menuItems={optionsForDeltaTime}
                        initial={initialDeltaTime}
                    />
                </Box>
            }
        />
    )

    const preRequestScriptComponent = (
        <LegacyCard sectioned title="Configure Pre-request script" key="preRequestScript"  primaryFooterAction={
            
                { 
                    content: "Save", destructive: false, onAction: () => {addOrUpdateScript()}
                }
            
        }>
            <Divider />
            <LegacyCard.Section>
                <div style={{ display: "grid", gridTemplateColumns: "1fr", gap: "10px", alignItems: "center" }}>
                    <TextField
                        placeholder="Enter pre-request javascript here..."
                        value={preRequestScript?.javascript || ""}
                        onChange={handlePreRequestScriptChange}
                        multiline={10}
                        monospaced
                        autoComplete="off"
                    />

                </div>
            </LegacyCard.Section>

            
        </LegacyCard>
    )

    async function saveCommonTemplate() {
        await api.saveCommonTestTemplate(commonTestTemplate2)
        setToastConfig({ isActive: true, isError: false, message: "Test template saved successfully!" })
    }

    const commonTemplateComponent = (
        <LegacyCard sectioned title="Configure global test configuration" key="commonTestTemplate" primaryFooterAction={
            {
                content: "Save", destructive: false, onAction: () => { saveCommonTemplate() }
            }
        }>
            <Divider />
            <LegacyCard.Section flush>
                <SampleData
                data={commonTestTemplate} 
                editorLanguage="custom_yaml" minHeight="240px" 
                readOnly={false} 
                getEditorData={setCommonTestTemplate2} />
            </LegacyCard.Section>
        </LegacyCard>
    )

    let components = [<TestCollectionConfiguration/>, rateLimit, updateDeltaPeriodTime, commonTemplateComponent]

    if (func.checkForFeatureSaas("TEST_PRE_SCRIPT")) {
        components.push(preRequestScriptComponent)
    }

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
                primaryAction={{ content: 'Stop all tests', onAction: handleStopAllTests }}
            />

    )
}

export default UserConfig