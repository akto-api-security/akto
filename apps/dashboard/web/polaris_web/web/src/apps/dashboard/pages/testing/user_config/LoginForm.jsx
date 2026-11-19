import { TextField, Text, Button, LegacyCard, LegacyTabs } from "@shopify/polaris"
import { useState } from "react"
import SampleData from "../../../components/shared/SampleData"
import api from "../api";
import func from "@/util/func"
import { usePermissions } from "@/util/permissions"
import AllowedAction from "../../../components/shared/AllowedAction"

// Login steps run as workflow nodes x1, x3, x5 ... so step k is node x(2k-1)
function stepNumberOf(nodeId) {
    const nodeNumber = parseInt(String(nodeId || '').replace(/^x/, ''), 10)
    return Number.isFinite(nodeNumber) && nodeNumber % 2 === 1 ? (nodeNumber + 1) / 2 : null
}

const PLACEHOLDER_HELP = {
    url: "Use ${xN.response.body.<path>} from earlier steps; add |urlencode for values inside a query string.",
    headers: "\"Cookie\": \"${cookies}\" sends the cookies earlier steps set that are valid for this URL (domain, path, Secure). ${cookies.<name>} is one cookie's value.",
    body: "Filters: ${key|regex:<pattern>} (group 1), ${key|urlencode}, ${key|raw} (no escaping). Form-only endpoint? Set Content-Type: application/x-www-form-urlencoded and write the body as JSON; it is sent URL-encoded.",
}

function LoginForm({ step, setSteps, miniTestingServiceName = '' }) {

    const [ selectedApiResponseTab, setSelectedApiResponseTab] = useState(0)
    const [testDisable, setTestDisable] = useState(false)
    const { canCall } = usePermissions()
    const stepNumber = stepNumberOf(step.id)

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
        setTestDisable(true)
        func.setToast(true,  false,  "Running login flow")
        try {
            const response = await api.triggerSingleStep(
                'LOGIN_REQUEST',
                step.id,
                [{ ...step }],
                miniTestingServiceName || undefined
            )
            if (response) {
                func.setToast(true,  false,  <div data-testid="login_flow_ran_message">Login flow ran successfully!</div>)
                const testResponse = JSON.parse(response.responses[0])

                let responseBody
                try {
                    responseBody = func.formatJsonForEditor(testResponse.body)
                } catch {
                    responseBody = testResponse.body
                }

                if(typeof responseBody === 'object' && Object.keys(responseBody).length > 0){
                    responseBody = JSON.stringify(responseBody)
                  }

                setSteps(prev => prev.map((s) => s.id === step.id ? {
                    ...s,
                    testResponse: {
                        headers: { message: func.formatJsonForEditor(testResponse.headers) },
                        body: {  message: responseBody }
                    }
                }
                : s))
                setSelectedApiResponseTab(0)
            }
            
        } 
        catch (Exception ) {
            
        }

        setTestDisable(false);

    }   

    return (
        <div>
            <Text variant="headingMd">Call API</Text>
            {stepNumber !== null &&
                <Text variant="bodyMd" color="subdued">
                    {`Later steps and auth params reference this step as \${${step.id}.response.body.<path>} or \${step${stepNumber}.response.body.<path>} (also .response.header.<name>, .response.regex).`}
                </Text>
            }
            <br />

            <div style={{ display: "grid", gridTemplateColumns: "40% 60%" }}>
                <div style={{ maxWidth: "90%" }}>
                    <div style={{ paddingRight: "20px" }}>
                        <TextField id={"url"} label="URL" value={step.url} requiredIndicator onChange={(url) => updateForm("url", url)} helpText={PLACEHOLDER_HELP.url} />
                        <br />
                        <TextField id={"query-params"} label="Query Params" value={step.queryParams} onChange={(queryParams) => updateForm("queryParams", queryParams)} />
                        <br />
                        <TextField id={"method"} label="Method" value={step.method} requiredIndicator onChange={(method) => updateForm("method", method)} />
                        <br />
                        <TextField id={"headers"} label="Headers" value={step.headers} onChange={(headers) => updateForm("headers", headers)} helpText={PLACEHOLDER_HELP.headers} />
                        <br />
                        <TextField id={"body"} label="Body" value={step.body} onChange={(body) => updateForm("body", body)} helpText={PLACEHOLDER_HELP.body} />
                        <br />
                        <TextField id={"regex"} label="Regex" value={step.regex} onChange={(regex) => updateForm("regex", regex)} helpText={`Group 1 of the first match is available to later steps as \${${step.id}.response.regex}.`} />
                    </div>
                </div>

                <LegacyCard subdued>
                    <div style={{ display: "grid", gridTemplateColumns: "auto max-content", gap: "10px", alignItems: "center", padding: "20px" }}>
                        <Text variant="headingMd">Test Response</Text>
                        <AllowedAction allowed={canCall('api/triggerSingleLoginFlow')}><Button id={"test-button"} onClick={handleLoginFlowTest} disabled={testDisable}>Test</Button></AllowedAction>
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