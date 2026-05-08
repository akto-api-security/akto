import { LegacyCard, Tabs, Text, Button, ButtonGroup, Divider, HorizontalStack, Checkbox } from '@shopify/polaris';
import { useState, useEffect } from 'react';
import SpinnerCentered from '../../../components/progress/SpinnerCentered';
import DropdownSearch from '../../../components/shared/DropdownSearch';
import TestingStore from '../testingStore';
import Store from '../../../store';
import LoginForm from './LoginForm';
import OtpVerification from './OtpVerification';
import api from '../api';
import { v4 as uuidv4 } from 'uuid';
import AuthParams from './AuthParams';

function LoginStepBuilder({extractInformation, showOnlyApi, setStoreData}) {

    const initialStepState = {
        id: "x1",
        content: "Step 1",
        body: '{"email": "abc@mail.com"}',
        headers: '{"content-type": "application/json"}',
        method: "POST",
        otpRefUuid: "",
        queryParams: "",
        regex: "(\d+){1,6}",
        type: "LOGIN_FORM",
        url: "https://xyz.com",
        allowAllStatusCodes: false,
        testResponse: ""
    }

    const [steps, setSteps] = useState([{
        ...initialStepState,
        id: "x1",
        content: "Step 1",
    }
    ])

    const [authParams, setAuthParams] = useState([{
        key: "",
        value: "",
        where: "HEADER",
        showHeader: true
    }])

    const setToastConfig = Store(state => state.setToastConfig)
    const authMechanism = TestingStore(state => state.authMechanism)

    const [selectedStep, setSelectedStep] = useState(0)
    const [isLoading, setIsLoading] = useState(false)

    useEffect(() => {

        if(extractInformation){
            setIsLoading(true)
            if (authMechanism && authMechanism.type === "LOGIN_REQUEST" && authMechanism.requestData[0].type !== "RECORDED_FLOW") {
                setSteps(authMechanism.requestData.map((step, index) => ({
                    ...step,
                    id: `x${2 * index + 1}`,
                    content: `Step ${index + 1}`,
                    testResponse: ''
                })))
                setAuthParams(authMechanism.authParams)
            }
            setSelectedStep(0)
            setIsLoading(false)
        }else{
            return;
        }
    }, [authMechanism])

    const stepOptions = [
        { label: "Call API", value: "LOGIN_FORM" },
        { label: "Receive OTP", value: "OTP_VERIFICATION" },
    ]

    const stepsTabs = steps.map(step => ({
        id: step.id,
        content: step.content
    }))

    function getStepDropdownLabel() {
        const type = stepOptions.find(stepOption => stepOption.value === steps[selectedStep].type)
        if (type) return type.label
        else return ""
    }

    function handleStepChange(step) {
        setSelectedStep(step)
    }

    function handleStatusCodeToggle(val) {
        setSteps(prev => prev.map((step, index) => index === selectedStep ? {
            ...step,
            allowAllStatusCodes: val
        }
        : step))
    }

    function handleStepTypeChange(type) {
        if (type === "LOGIN_FORM") {
            setSteps(prev => prev.map((step, index) => index === selectedStep ? {
                ...initialStepState,
                id: step.id,
                content: step.content,
                type: type,
            }
            : step))
        } else {
            setSteps(prev => prev.map((step, index) => index === selectedStep ? {
                ...initialStepState,
                id: step.id,
                content: step.content,
                type: type,
                otpRefUuid: uuidv4()
            }
            : step))
        }

    }

    function handleAddStep () {
        setSteps(prev => {
            setSelectedStep(prev.length)
            return [...prev, {
                ...initialStepState,
                id: `x${2 * prev.length + 1}`,
                content: `Step ${prev.length + 1}`,
                type: "LOGIN_FORM",
            }]
        })
        setToastConfig({ isActive: true, isError: false, message: "Step added!" })
    }

    function handleRemoveStep () {
        if (steps.length > 1) {
            setSteps(prev => {
                setSelectedStep(prev.length - 2)
                const prevFiltered = prev.filter((step, index) => index !== selectedStep)
                const prevIndexFixed = [...prevFiltered]
                for(let i = 0; i < prevIndexFixed.length; i++) {
                    prevIndexFixed[i].id = `x${2 * i + 1}`
                    prevIndexFixed[i].content = `Step ${i + 1}`
                }
                return prevIndexFixed
            })
            setToastConfig({ isActive: true, isError: false, message: "Step removed!" })
        } else {
            setToastConfig({ isActive: true, isError: true, message: "Atleast 1 step required!" })
        }
    }

    async function handleSave() {
        await api.addAuthMechanism('LOGIN_REQUEST', [ ...steps ] , authParams)
        setToastConfig({ isActive: true, isError: false, message: <div data-testid="login_flow_success_message">Login flow saved successfully!</div> })

    }

    useEffect(() => {
        if(extractInformation){
            setStoreData({
                steps:steps,
                authParams: authParams
            })
        }else{
            return;
        }
    },[steps,authParams])

    return (
        <div>
            <Text variant="headingMd">Login Step Builder</Text>

            <br />

            {isLoading ? <SpinnerCentered /> :
                <div>
                    <LegacyCard>
                        <div style={{ display: "grid", gridTemplateColumns: "auto max-content", alignItems: "center", padding: "10px" }}>
                            <Tabs tabs={stepsTabs} selected={selectedStep} onSelect={handleStepChange}></Tabs>
                            <HorizontalStack gap={"2"}>
                                <Checkbox
                                    label='Allow All Status codes'
                                    checked={steps[selectedStep].allowAllStatusCodes}
                                    onChange={() => handleStatusCodeToggle(!steps[selectedStep].allowAllStatusCodes)}
                                />
                                <Button id={"add-step-button"} primary onClick={handleAddStep}>Add step</Button>
                            </HorizontalStack>
                        </div>

                        <Divider />

                        <LegacyCard.Section>
                        {showOnlyApi !==null && showOnlyApi ? null : <div style={{ display: "grid", gridTemplateColumns: "max-content max-content", gap: "10px", alignItems: "center" }}>
                                <Text>Select step type:</Text>
                                 <DropdownSearch
                                    id={"select-step-type-menu"}
                                    placeholder="Select step type"
                                    optionsList={stepOptions}
                                    setSelected={handleStepTypeChange}
                                    preSelected={"Call API"}
                                    value={getStepDropdownLabel()}
                                />
                            </div>
                        }
                            <br />
                            <div>
                                {steps[selectedStep].type === "LOGIN_FORM" && <LoginForm step={steps[selectedStep]} setSteps={setSteps}/>}
                                {steps[selectedStep].type === "OTP_VERIFICATION" && <OtpVerification step={steps[selectedStep]} setSteps={setSteps}/>}
                                <br />
                                <Button id={"remove-step-button"} destructive onClick={handleRemoveStep}>Remove step</Button>
                            </div>
                        </LegacyCard.Section>

                    </LegacyCard>

                    <AuthParams authParams={authParams} setAuthParams={setAuthParams}/>

                    <br />
                    {showOnlyApi ? null :<Button id={"save-token"} primary onClick={handleSave}><div data-testid="save_token_automated">Save changes</div></Button>}

                </div>
            }
        </div>
    )
}

export default LoginStepBuilder