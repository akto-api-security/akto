import { Divider, FormLayout, HorizontalGrid, HorizontalStack, Text } from "@shopify/polaris"
import Dropdown from "../../../components/layouts/Dropdown"
import DropdownSearch from "../../../components/shared/DropdownSearch"
import { useState } from "react"
import LoginStepBuilder from "./LoginStepBuilder"
import JsonRecording from "./JsonRecording"


function Automated() {

    const [automationType, setAutomationType] = useState("LOGIN_STEP_BUILDER")

    const automationOptions = [
        { label: "Login Step Builder", value: "LOGIN_STEP_BUILDER" },
        { label: "JSON Recording", value: "RECORDED_FLOW" },
    ]
    function getAutomationTypeDropdownLabel() {
        const type = automationOptions.find(automationOption => automationOption.value === automationType)
        if (type) return type.label
        else return ""
    }

    return (
        <div>
            <Text variant="headingMd">Automate attacker auth token generation</Text>
            <br />
            <div style={{ display: "grid", gridTemplateColumns: "max-content max-content", gap: "10px", alignItems: "center"}}>
                <Text>Select automation type:</Text>
                <DropdownSearch
                    placeholder="Select automation type"
                    optionsList={automationOptions}
                    setSelected={(type) => setAutomationType(type)}
                    preSelected={"Login Step Builder"}
                    value={getAutomationTypeDropdownLabel()}
                />
            </div>

            <br />

            <div style={{ minHeight: "75vh" }}>
                {automationType === "LOGIN_STEP_BUILDER" && <LoginStepBuilder />}
                {automationType === "RECORDED_FLOW" && <JsonRecording />}
            </div> 
        </div>
    )
}

export default Automated