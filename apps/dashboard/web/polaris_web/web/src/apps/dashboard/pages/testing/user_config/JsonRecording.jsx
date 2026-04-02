import { CalloutCard, Text, TextField, Card, Button, Spinner, Modal, VerticalStack, Box } from "@shopify/polaris"
import { useEffect, useRef, useState } from "react"
import TestingStore from "../testingStore"
import api from "../api"
import Store from "../../../store"
import AuthParams from "./AuthParams"

async function fetchRecordedLoginScreenshotsList(trimmedRole) {
    const resp = await api.fetchRecordedLoginScreenshots(trimmedRole)
    const raw = resp?.screenshotsBase64
    return Array.isArray(raw) ? raw : []
}

function ReplayScreenshotImg({ b64, stepIndex }) {
    const [fmt, setFmt] = useState('jpeg')
    return (
        <img
            alt={`Replay step ${stepIndex + 1}`}
            src={`data:image/${fmt};base64,${b64}`}
            onError={() => { if (fmt === 'jpeg') setFmt('png') }}
            style={{ maxWidth: '100%', marginBottom: '12px', display: 'block', borderRadius: '4px' }}
        />
    )
}

function JsonRecording({extractInformation, showOnlyApi, setStoreData, roleName}) {

    const authMechanism = TestingStore(state => state.authMechanism)
    const setToastConfig = Store(state => state.setToastConfig)
    const [tokenFetchCommand, setTokenFetchCommand] = useState('"Bearer " + JSON.parse(Object.values(window.localStorage).find(x => x.indexOf("access_token")> -1)).body.access_token')
    const [content, setContent] = useState("")
    const [extractedToken, setExtractedToken] = useState("")
    const [showVerify, setShowVerify] = useState(false)
    const [isLoading, setIsLoading] = useState(false)

    const [screenshotsModalOpen, setScreenshotsModalOpen] = useState(false)
    const [modalScreenshots, setModalScreenshots] = useState([])
    const [modalLoading, setModalLoading] = useState(false)
    const [hasScreenshots, setHasScreenshots] = useState(false)

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
                pollExtractedToken(roleName)
            }
        } else {
            return;
        }
    }, [authMechanism])

    useEffect(() => {
        const trimmed = roleName && String(roleName).trim()
        if (!trimmed) {
            setHasScreenshots(false)
            return
        }
        let cancelled = false

        async function syncHasScreenshotsFromServer() {
            try {
                const list = await fetchRecordedLoginScreenshotsList(trimmed)
                if (!cancelled) {
                    setHasScreenshots(list.length > 0)
                }
            } catch {
                if (!cancelled) {
                    setHasScreenshots(false)
                }
            }
        }

        syncHasScreenshotsFromServer()
        return () => {
            cancelled = true
        }
    }, [roleName])

    const inputRef = useRef(null);

    const handleClick = () => {
        inputRef.current.click();
    };

    function sleep(ms) {
        return new Promise(resolve => setTimeout(resolve, ms));
    }

    async function pollExtractedToken(screenshotRoleName) {
        setIsLoading(true)
        const initialWaitPeriod = 5000
        await sleep(initialWaitPeriod)

        const pollAttempts = 20
        const pollSleepDuration = 10000
        let finishedOk = false
        const trimmedRole = screenshotRoleName && String(screenshotRoleName).trim()

        async function refreshScreenshotsWhileActive() {
            if (!trimmedRole) return
            try {
                const list = await fetchRecordedLoginScreenshotsList(trimmedRole)
                setHasScreenshots(list.length > 0)
            } catch {
                /* ignore */
            }
        }

        for (let i = 0; i < pollAttempts; i++) {
            if (i > 0) {
                await sleep(i * pollSleepDuration)
            }

            try {
                const resp = await api.fetchRecordedLoginFlow("x1")
                if (trimmedRole && resp.tokenFetchInProgress) {
                    await refreshScreenshotsWhileActive()
                }
                if (!resp.tokenFetchInProgress) {
                    setExtractedToken(resp.token)
                    finishedOk = true
                    setIsLoading(false)
                    setToastConfig({ isActive: true, isError: false, message: "Verify extracted token" })
                    if (trimmedRole) {
                        await refreshScreenshotsWhileActive()
                    }
                    break
                }
            } catch {
                /* continue polling */
            }
        }

        if (!finishedOk) {
            setIsLoading(false)
            setToastConfig({ isActive: true, isError: true, message: "Error while extracting token using JSON recording" })
        }
    }

    async function openReplayScreenshotsModal() {
        if (!roleName || !roleName.trim()) {
            return
        }
        setScreenshotsModalOpen(true)
        setModalLoading(true)
        setModalScreenshots([])
        try {
            const list = await fetchRecordedLoginScreenshotsList(roleName.trim())
            setModalScreenshots(list)
            setHasScreenshots(list.length > 0)
        } catch (err) {
            setToastConfig({ isActive: true, isError: true, message: `Could not load screenshots: ${err}` })
        } finally {
            setModalLoading(false)
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
            const result = api.uploadRecordedLoginFlow(reader.result, tokenFetchCommand, roleName)

            result.then((resp) => {
                setToastConfig({ isActive: true, isError: false, message: "JSON recording uploaded" })
                setShowVerify(true)
                const trimmed = roleName && String(roleName).trim()
                if (trimmed) {
                    setHasScreenshots(false)
                }
                pollExtractedToken(roleName)
            }).catch((err) => {
                setToastConfig({ isActive: true, isError: true, message: `Upload JSON recording failed. Error: ${err}` })
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

                {hasScreenshots && (
                    <Box paddingBlockStart="4">
                        <Button variant="plain" onClick={openReplayScreenshotsModal}>
                            View replay screenshots
                        </Button>
                    </Box>
                )}

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

            <Modal
                open={screenshotsModalOpen}
                onClose={() => setScreenshotsModalOpen(false)}
                title="Replay screenshots"
                large
                primaryAction={{
                    content: 'Close',
                    onAction: () => setScreenshotsModalOpen(false),
                }}
            >
                <Modal.Section>
                    {modalLoading ? (
                        <Box width="100%" minHeight="120px" display="flex" alignItems="center" justifyContent="center">
                            <Spinner size="small" />
                        </Box>
                    ) : modalScreenshots.length === 0 ? (
                        <Text as="p">No screenshots stored for this role yet. Upload a JSON recording to capture replay steps.</Text>
                    ) : (
                        <VerticalStack gap="3">
                            {modalScreenshots.map((b64, i) => (
                                <ReplayScreenshotImg key={i} b64={b64} stepIndex={i} />
                            ))}
                        </VerticalStack>
                    )}
                </Modal.Section>
            </Modal>

            <AuthParams authParams={authParams} setAuthParams={setAuthParams}/>

            <br />
            { showOnlyApi ? null : <Button id={"save-token"} onClick={handleSave} primary>Save changes</Button> }
        </div>
    )
}

export default JsonRecording
