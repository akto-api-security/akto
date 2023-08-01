import { TextField, Text, Button, Divider, LegacyCard, LegacyTabs, Tabs } from "@shopify/polaris"
import { useEffect, useState } from "react"
import SampleData from "../../../components/shared/SampleData"
import api from "../api";
import Store from "../../../store";

function LoginForm({ step, setSteps }) {

    const setToastConfig = Store(state => state.setToastConfig)
    const [ selectedApiResponseTab, setSelectedApiResponseTab] = useState(0)

    function updateForm(field, value) {
        setSteps(prev => prev.map((s) => s.id === step.id ? {
            ...s,
            [field]: value
        } : s))
    }

    const apiTestResponseTabs = [
        {
            id: 'headers',
            content: 'Headers',
        },
        {
            id: 'body',
            content: 'Body',
        }
    ];

    async function handleLoginFlowTest() {
        const response = await api.triggerSingleStep('LOGIN_REQUEST', step.id, [{ ...step }])
        if (response) {
            setToastConfig({ isActive: true, isError: false, message: "Login flow ran successfully!"})
            const testResponse = JSON.parse(response.responses[0])

            let responseBody
            try {
                responseBody = JSON.parse(testResponse.body)
            } catch {
                responseBody = testResponse.body
            }

            setSteps(prev => prev.map((s) => s.id === step.id ? {
                ...s,
                testResponse: {
                    headers: { firstLine: "", json: JSON.parse(testResponse.headers) },
                    body: { firstLine: "", json: responseBody }
                }
            }
            : s))
            setSelectedApiResponseTab(0)
        }
    }   

    return (
        <div>
            <Text variant="headingMd">Call API</Text>
            <br />

            <div style={{ display: "grid", gridTemplateColumns: "40% 60%" }}>
                <div style={{ maxWidth: "90%" }}>
                    <div style={{ paddingRight: "20px" }}>
                        <TextField label="URL" value={step.url} requiredIndicator onChange={(url) => updateForm("url", url)} />
                        <br />
                        <TextField label="Query Params" value={step.queryParams} onChange={(queryParams) => updateForm("queryParams", queryParams)} />
                        <br />
                        <TextField label="Method" value={step.method} requiredIndicator onChange={(method) => updateForm("method", method)} />
                        <br />
                        <TextField label="Headers" value={step.headers} onChange={(headers) => updateForm("headers", headers)} />
                        <br />
                        <TextField label="Body" value={step.body} onChange={(body) => updateForm("body", body)} />
                    </div>
                </div>

                <LegacyCard subdued>
                    <div style={{ display: "grid", gridTemplateColumns: "auto max-content", gap: "10px", alignItems: "center", padding: "20px" }}>
                        <Text variant="headingMd">Test Response</Text>
                        <Button onClick={handleLoginFlowTest}>Test</Button>
                    </div>
                    {step.testResponse ?
                        <div>
                            <LegacyTabs tabs={apiTestResponseTabs} selected={selectedApiResponseTab} onSelect={selected => setSelectedApiResponseTab(selected)}/>
                            <SampleData data={ selectedApiResponseTab === 0 ? step.testResponse.headers : step.testResponse.body }/>
                        </div>
                        : <div style={{ height: "100%", background: "#FFFFFF", padding: "10px" }}>
                            Click on the "Test" button to get the response
                        </div>
                    }
                </LegacyCard>
            </div>
        </div>

    )
}

export default LoginForm