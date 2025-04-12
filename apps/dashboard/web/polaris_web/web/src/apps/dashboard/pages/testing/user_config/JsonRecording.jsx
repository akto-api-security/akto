import { CalloutCard, LegacyCard, Text, TextField, Card, Button, Spinner, Divider } from "@shopify/polaris"
import { useEffect, useRef, useState } from "react"
import TestingStore from "../testingStore"
import api from "../api"
import Store from "../../../store"
import AuthParams from "./AuthParams"

function JsonRecording({extractInformation, showOnlyApi, setStoreData}) {

    const fileInputRef = useRef("")

    const authMechanism = TestingStore(state => state.authMechanism)
    const setToastConfig = Store(state => state.setToastConfig)
    const [tokenFetchCommand, setTokenFetchCommand] = useState('"Bearer " + JSON.parse(Object.values(window.localStorage).find(x => x.indexOf("access_token")> -1)).body.access_token')
    const [content, setContent] = useState("")
    const [extractedToken, setExtractedToken] = useState("")
    const [showVerify, setShowVerify] = useState(false)
    const [isLoading, setIsLoading] = useState(false)

    const [authParams, setAuthParams] = useState([{
        key: "",
        value: "",
        where: "HEADER",
        showHeader: true
    }])

    useEffect(() => {
        if (extractInformation) {
            if (authMechanism && authMechanism.type === "LOGIN_REQUEST" && authMechanism.requestData[0].type === "RECORDED_FLOW") {
                setTokenFetchCommand(authMechanism.requestData[0].tokenFetchCommand)
                setAuthParams(authMechanism.authParams)
                setShowVerify(true)
                pollExtractedToken()
            }
        } else {
            return;
        }
    }, [authMechanism])

    const inputRef = useRef(null);

    const handleClick = () => {
        inputRef.current.click();
    };

    function sleep(ms) {
        return new Promise(resolve => setTimeout(resolve, ms));
    }

    async function pollExtractedToken() {
        setIsLoading(true)
        let initialWaitPeriod = 5000

        await sleep(initialWaitPeriod);

        let pollAttempts = 20
        let pollSleepDuration = 10000
        let success = false
        for (let i = 0; i < pollAttempts; i++) {
            if (success) {
                break
            } else {
                await sleep((i) * pollSleepDuration);
            }

            let result = api.fetchRecordedLoginFlow("x1")

            result.then((resp) => {
                if (!resp.tokenFetchInProgress) {
                    setExtractedToken(resp.token)
                    success = true
                    setIsLoading(false)
                    setToastConfig({ isActive: true, isError: false, message: "Verify extracted token" })
                }
            }).catch((err) => {
            })
        }
        if (!success) {
            setToastConfig({ isActive: true, isError: true, message: "Error while extracting token using JSON recording" })
        }
    }

    function handleFileChange(event) {
        setShowVerify(false)
        const fileObj = event.target.files && event.target.files[0];
        if (!fileObj) {
            return;
        }

        const reader = new FileReader()
        reader.readAsText(fileObj)

        reader.onload = () => {
            setContent(reader.result)
            const result = api.uploadRecordedLoginFlow(reader.result, tokenFetchCommand)

            result.then((resp) => {
                setToastConfig({ isActive: true, isError: false, message: "JSON recording uploaded" })
                setShowVerify(true)
                pollExtractedToken()
            }).catch((err) => {
                setToastConfig({ isActive: true, isError: false, message: `Upload JSON recording failed. Error: ${err}` })
            })
        }

    }

    async function handleSave() {
        const steps = [{
            tokenFetchCommand: tokenFetchCommand,
            type: "RECORDED_FLOW"
        }]
        await api.addAuthMechanism('LOGIN_REQUEST', [...steps], authParams)
        setToastConfig({ isActive: true, isError: false, message: "JSON recording flow saved successfully!" })
    }

    useEffect(() => {
        if(extractInformation){
            const steps = [{
                tokenFetchCommand: tokenFetchCommand,
                content: content,
                type: "RECORDED_FLOW"
            }]

            setStoreData({
                steps:steps,
                authParams: authParams,
            })
        }else{
            return;
        }
    },[content, tokenFetchCommand, authParams])

    return (
        <div>
            <CalloutCard
                title="Upload JSON Recording"
                padding='10px'
                primaryAction={{
                    id:"upload-json-button",
                    content: 'Upload JSON Recording',
                    onAction: handleClick,
                }}
            >
                <Text variant="headingMd">Steps To Add Recording</Text>
                <Text variant="bodyMd">Step 1: Please Open your browser and click on open chrome dev tools</Text>
                <Text variant="bodyMd">Step 2: Open recorder tab and click on "Start a New Recording"</Text>
                <Text variant="bodyMd">Step 3: Run the complete login flow, and stop the recording once done.</Text>
                <Text variant="bodyMd">Step 4: Click on the download icon just above, and select "Export as a json file"</Text>
                <Text variant="bodyMd">Step 4: Go to akto dashboard and specify the token fetch command, which will be used by AKTO to extract the token</Text>
                <Text variant="bodyMd">Step 5: Specify the json script in AKTO Dashboard, and wait for couple of minutes for verifying the extracted token</Text>

                <br />
                <TextField id={"token-input-field"} label="Token Fetch Command" value={tokenFetchCommand} onChange={(command) => setTokenFetchCommand(command)} />

                <input
                    style={{ display: 'none' }}
                    ref={inputRef}
                    type="file"
                    accept=".json"
                    onChange={handleFileChange}
                />

                {showVerify &&
                    <Card>
                        <Text variant="headingMd">Verify Extracted token:</Text>
                        {isLoading ?
                            <div style={{ width: "100%", height: "100px", display: "flex", alignItems: "center", justifyContent: "center" }}>
                                <Spinner size="small" />
                            </div> :
                            <div>
                                <br />
                                <TextField value={extractedToken} readonly />
                            </div>
                        }

                    </Card>
                }

            </CalloutCard>

            <AuthParams authParams={authParams} setAuthParams={setAuthParams}/>

            <br />
            { showOnlyApi ? null : <Button id={"save-token"} onClick={handleSave} primary>Save changes</Button> }
        </div>
    )
}

export default JsonRecording
