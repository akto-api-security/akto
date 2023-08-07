import { CalloutCard, LegacyCard, Text, TextField, Card, Button, Spinner } from "@shopify/polaris"
import { useEffect, useRef, useState } from "react"
import TestingStore from "../testingStore"
import api from "../api"
import Store from "../../../store"

function OtpVerification({ step, setSteps }) {

    const authMechanism = TestingStore(state => state.authMechanism)
    const setToastConfig = Store(state => state.setToastConfig)
    const [extractedOTP, setExtractedOTP] = useState("")
    const [showVerify, setShowVerify] = useState(false)
    const [isLoading, setIsLoading] = useState(true)

    const webhookUrl = `${window.location.origin}/saveOtpData/${step.otpRefUuid}`

    useEffect(() => {
        if (authMechanism && authMechanism.type === "LOGIN_REQUEST" && authMechanism.requestData[0].type === "OTP_VERIFICATION") {
            setShowVerify(true)
            pollOtpResponse()
        }
    }, [])

    function updateRegex(regex) {
        setSteps(prev => prev.map((s) => s.id === step.id ? {
            ...s,
            regex: regex
        } : s))
    }

    function sleep(ms) {
        return new Promise(resolve => setTimeout(resolve, ms));
    }

    async function pollOtpResponse() {
        setShowVerify(false)
        setIsLoading(true)
        const fetchUrl = window.location.origin + "/api/fetchOtpData/" + step.otpRefUuid

        const pollAttempts = 20
        const pollSleepDuration = 10000

        let success = false
        for (let i=0; i<pollAttempts; i++) {
            if (success) {
                break
            } else {
                await sleep((i) * pollSleepDuration);
            }
            const result = api.fetchOtpData(fetchUrl)
            result.then((resp) => {
                    respJson = JSON.parse(resp)
                    setToastConfig({ isActive: true, isError: true, message: "Verify extracted OTP" })
                    setExtractedOTP(respJson.otpText)
                    setIsLoading(false)
                    success = true
                }).catch((err) => {
                })
            }
            if (!success) {
                setToastConfig({ isActive: true, isError: true, message: "Error while extracting OTP" })
            }
    }

    return (
        <div>
            <CalloutCard
                title="Receive OTP"
                padding='10px'
                primaryAction={{
                    id: "zapier-setup-button",
                    content: 'Zapier setup done',
                    onAction: pollOtpResponse,
                }}
            >
                <Text variant="headingMd">Steps To setup Webhook</Text>
                <Text variant="bodyMd">Step 1: Connect with zapier to send data to the following webhook url - {webhookUrl}</Text>
                <Text variant="bodyMd">Step 2: After finishing setup, click on "Webhook Setup Done" button for fetching OTP content</Text>
                <Text variant="bodyMd">Step 3: Specify a regex for extracting the OTP code from the content</Text>
                <Text variant="bodyMd">Step 4: Verify the extracted OTP value and click on "SAVE"</Text>

                <br />
                <TextField id={"regex-input-field"} label="Regex to extract OTP" value={step.regex} onChange={(regex) => updateRegex(regex)} />

                {showVerify &&
                    <Card>
                        <Text variant="headingMd">Extracted OTP:</Text>
                        {isLoading ?
                            <div style={{ width: "100%", height: "100px", display: "flex", alignItems: "center", justifyContent: "center" }}>
                                <Spinner size="small" />
                            </div> :
                            <div>
                                <br />
                                <TextField value={extractedOTP} readonly />
                            </div>
                        }

                    </Card>
                }

            </CalloutCard>
        </div>
    )
}

export default OtpVerification
