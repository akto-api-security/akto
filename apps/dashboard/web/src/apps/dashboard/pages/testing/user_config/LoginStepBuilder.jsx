import { LegacyCard, Tabs, Text, Button, ButtonGroup, Divider } from '@shopify/polaris';
import { useState, useEffect } from 'react';
import SpinnerCentered from '../../../components/progress/SpinnerCentered';
import DropdownSearch from '../../../components/shared/DropdownSearch';
import TestingStore from '../testingStore';
import Store from '../../../store';
import LoginForm from './LoginForm';
import OtpVerification from './OtpVerification';

function LoginStepBuilder() {
    

    const [steps, setSteps] = useState([{
        id: "step1",
        content: "Step 1",
        headers: "",
        method: "",
        otpRefUuid: "",
        queryParams: "",
        regex: "",
        type: "LOGIN_FORM",
        url: "",
        testResponse: ""
    }
    ])

    const setToastConfig = Store(state => state.setToastConfig)
    const authMechanism = TestingStore(state => state.authMechanism)

    const [selectedStep, setSelectedStep] = useState(0)
    const [isLoading, setIsLoading] = useState(true)

    useEffect(() => {
        setIsLoading(true)
        if (authMechanism && authMechanism.type === "LOGIN_REQUEST") {
            //add handle data from api logic - id, content, testResponse
            setSteps([
                {
                    id: 'step1',
                    content: 'Step 1',
                },
            ])
        }
        setSelectedStep(0)
        setIsLoading(false)
    }, [])

    const stepOptions = [
        { label: "Call API", value: "LOGIN_FORM" },
        { label: "Receive OTP", value: "OTP_VERIFICATION" },
    ]

    function getStepDropdownLabel() {
        const type = stepOptions.find(stepOption => stepOption.value === steps[selectedStep].type)
        if (type) return type.label
        else return ""
    }

    function handleStepChange(step) {
        setSelectedStep(step)
    }

    function handleStepTypeChange(type) {
        setSteps(prev => prev.map((step, index) => index === selectedStep ? {
                id: step.id,
                content: step.content,
                headers: "",
                method: "",
                otpRefUuid: "",
                queryParams: "",
                regex: "",
                type: type,
                url: "",
                testResponse: ""
            }
            : step))
    }

    function handleAddStep () {
        setSteps(prev => {
            setSelectedStep(prev.length)
            return [...prev, {
                id: `step${prev.length + 1}`,
                content: `Step ${prev.length + 1}`,
                headers: "",
                method: "",
                otpRefUuid: "",
                queryParams: "",
                regex: "",
                type: type,
                url: ""
            }]
        })
        setToastConfig({ isActive: true, isError: false, message: "Step added!" })
    }

    function handleRemoveStep () {
        if (steps.length > 1) {
            setSteps(prev => {
                setSelectedStep(prev.length - 2)
                return prev.filter((step, index) => index !== selectedStep)
            })
            setToastConfig({ isActive: true, isError: false, message: "Step removed!" })
        } else {
            setToastConfig({ isActive: true, isError: true, message: "Atleast 1 step required!" })
        }
    }

    return (
        <div>
            <Text variant="headingMd">Login Step Builder</Text>

            <br />

            {isLoading ? <SpinnerCentered /> :
                <div>
                    <LegacyCard>
                        <div style={{ display: "grid", gridTemplateColumns: "auto max-content", alignItems: "center", padding: "10px" }}>
                            <Tabs tabs={steps} selected={selectedStep} onSelect={handleStepChange}></Tabs>
                            <Button primary onClick={handleAddStep}>Add step</Button>
                        </div>

                        <Divider />

                        <LegacyCard.Section>
                            <div style={{ display: "grid", gridTemplateColumns: "max-content max-content", gap: "10px", alignItems: "center" }}>
                                <Text>Select step type:</Text>
                                <DropdownSearch
                                    placeholder="Select step type"
                                    optionsList={stepOptions}
                                    setSelected={handleStepTypeChange}
                                    preSelected={"Call API"}
                                    value={getStepDropdownLabel()}
                                />
                            </div>
                            <br />
                            <div>
                                {steps[selectedStep].type === "LOGIN_FORM" && <LoginForm step={steps[selectedStep]} setSteps={setSteps}/>}
                                {steps[selectedStep].type === "OTP_VERIFICATION" && <OtpVerification step={steps[selectedStep]} setSteps={setSteps}/>}
                                <br />
                                <Button destructive onClick={handleRemoveStep}>Remove step</Button>
                            </div>
                        </LegacyCard.Section>

                    </LegacyCard>

                    <br />
                    <Button primary>Save changes</Button>

                </div>
            }
        </div>
    )
}

export default LoginStepBuilder