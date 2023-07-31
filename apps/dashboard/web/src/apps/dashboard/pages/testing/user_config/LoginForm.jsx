import { TextField, Text } from "@shopify/polaris"
import { useEffect, useState } from "react"
import SampleData from "../../../components/shared/SampleData"

function LoginForm({ step, setSteps }) {
    // initial state to be reused everywhere
    // request headers and payload
    // receive otp 
    // json recording 

    function updateForm(field, value) {
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

    return (
        <div>
            <Text variant="headingMd">Call API</Text>
            <br />

            <div style={{ display: "grid", gridTemplateColumns: "50% 50%" }}>
                <div style={{maxWidth: "75%"}}>
                    <div style={{ paddingRight: "20px" }}>
                        <TextField label="URL" value={step.url} requiredIndicator onChange={(name) => updateWebhookState("name", name)} />
                        <br />
                        <TextField label="Query Params" value={step.queryParams} onChange={(name) => updateWebhookState("name", name)} />
                        <br />
                        <TextField label="Method" value={step.method} requiredIndicator onChange={(name) => updateWebhookState("name", name)} />
                        <br />
                        <TextField label="Headers" value={step.headers} requiredIndicator onChange={(name) => updateWebhookState("name", name)} />
                        <br />
                        <TextField label="Body" value={step.body} requiredIndicator onChange={(name) => updateWebhookState("name", name)} />
                    </div>
                </div>
               
                <div>
                    <SampleData />
                </div>
            </div>
        </div>

    )
}

export default LoginForm