import { useState, useRef } from "react"
import { Banner, Box, Button, HorizontalStack, Spinner, Text, VerticalStack } from "@shopify/polaris"
import settingRequests from "../../settings/api"
import ConfigTextEditor from "./ConfigTextEditor"

// Maps a gateway/cyborg errorReason onto a message a dashboard user can act on.
// Keep this in sync with the errorReason values set in akto-gateway's
// config_remediation_applier.go and cyborg's EndpointConfigRemediationCleanupCron.
function describeErrorReason(errorReason) {
    switch (errorReason) {
        case "file_changed_since_review":
            return "This file changed on the device since you opened it. Reload the latest version and try again."
        case "path_not_allowed":
            return "This file path isn't recognized as a supported config file, so the save was blocked for safety."
        case "parse_failed":
            return "The file wasn't saved because the text isn't valid JSON/TOML. Fix the syntax and try again."
        case "io_error":
            return "The device couldn't read or write the file. Try again."
        case "timeout":
        case "presumed_dead_agent":
            return "The device didn't respond in time. It may be offline or the Akto agent may not be running."
        case "expired":
            return "This request expired before the device picked it up. Try again."
        default:
            return "The fix couldn't be applied. Please try again."
    }
}

// STEP is a simple state machine: idle -> fetching -> editing -> applying -> done/failed.
const STEP = {
    IDLE: "idle",
    FETCHING: "fetching",
    EDITING: "editing",
    APPLYING: "applying",
    DONE: "done",
    FAILED: "failed"
}

const POLL_INTERVAL_MS = 2000
const MAX_POLL_ATTEMPTS = 150 // ~5 minutes at 2s, matches the gateway's PREVIEW/APPLY job expiries

function ConfigTextEditorLanguage(format) {
    return format === "JSON" ? "json" : "plaintext"
}

async function pollExecution(remediationId, onEachAttempt) {
    for (let attempt = 0; attempt < MAX_POLL_ATTEMPTS; attempt++) {
        const resp = await settingRequests.fetchEndpointConfigRemediationExecutions(remediationId, 1)
        const execution = (resp?.executions || [])[0]
        if (execution && (execution.status === "COMPLETED" || execution.status === "FAILED")) {
            return execution
        }
        onEachAttempt?.(execution)
        await new Promise((resolve) => setTimeout(resolve, POLL_INTERVAL_MS))
    }
    return null
}

// Renders above the read-only remediation markdown when a finding is tied to a
// specific config file on a specific device. Walks: Idle -> fetch current
// content -> free-text edit -> save -> poll -> success/failure.
function ConfigRemediationAction({ toolName, configPath, format, host, deviceId, findingRefId }) {
    const [step, setStep] = useState(STEP.IDLE)
    const [content, setContent] = useState("")
    const [checksum, setChecksum] = useState("")
    const [errorReason, setErrorReason] = useState("")
    const editedContentRef = useRef("")

    const startFetch = async () => {
        setStep(STEP.FETCHING)
        setErrorReason("")
        try {
            const queueResp = await settingRequests.queueEndpointConfigRemediation(
                "PREVIEW", toolName, configPath, format, undefined, undefined, findingRefId, host, deviceId
            )
            const remediationId = queueResp?.remediationId
            if (!remediationId) {
                setStep(STEP.FAILED)
                setErrorReason("unknown")
                return
            }
            const execution = await pollExecution(remediationId)
            if (!execution || execution.status !== "COMPLETED") {
                setStep(STEP.FAILED)
                setErrorReason(execution?.errorReason || "timeout")
                return
            }
            setContent(execution.resultContent || "")
            editedContentRef.current = execution.resultContent || ""
            setChecksum(execution.resultChecksum || "")
            setStep(STEP.EDITING)
        } catch (err) {
            setStep(STEP.FAILED)
            setErrorReason("unknown")
        }
    }

    const saveEdits = async () => {
        setStep(STEP.APPLYING)
        setErrorReason("")
        try {
            const queueResp = await settingRequests.queueEndpointConfigRemediation(
                "APPLY", toolName, configPath, format, editedContentRef.current, checksum, findingRefId, host, deviceId
            )
            const remediationId = queueResp?.remediationId
            if (!remediationId) {
                setStep(STEP.FAILED)
                setErrorReason("unknown")
                return
            }
            const execution = await pollExecution(remediationId)
            if (!execution || execution.status !== "COMPLETED") {
                // If the device reports the file changed since review, hand back the
                // latest content/checksum so the user can re-review without losing
                // their own edits (kept in editedContentRef).
                if (execution?.errorReason === "file_changed_since_review" && execution?.resultContent) {
                    setContent(execution.resultContent)
                    setChecksum(execution.resultChecksum || "")
                }
                setStep(STEP.FAILED)
                setErrorReason(execution?.errorReason || "timeout")
                return
            }
            setStep(STEP.DONE)
        } catch (err) {
            setStep(STEP.FAILED)
            setErrorReason("unknown")
        }
    }

    const reReview = () => {
        setStep(STEP.IDLE)
        startFetch()
    }

    if (step === STEP.IDLE) {
        return (
            <Box paddingBlockEnd="4">
                <Button onClick={startFetch}>Edit File</Button>
            </Box>
        )
    }

    if (step === STEP.FETCHING) {
        return (
            <Box paddingBlockEnd="4">
                <HorizontalStack gap="2">
                    <Spinner size="small" />
                    <Text>Fetching the current file content from the device...</Text>
                </HorizontalStack>
            </Box>
        )
    }

    if (step === STEP.EDITING || step === STEP.APPLYING) {
        return (
            <VerticalStack gap="3">
                <Text variant="bodyMd" color="subdued">
                    Editing {configPath}. Changes are only saved to the device when you click Save.
                </Text>
                <ConfigTextEditor
                    value={content}
                    onChange={(val) => { editedContentRef.current = val }}
                    language={ConfigTextEditorLanguage(format)}
                    readOnly={step === STEP.APPLYING}
                />
                <HorizontalStack gap="2">
                    <Button primary onClick={saveEdits} loading={step === STEP.APPLYING}>
                        Save to Device
                    </Button>
                    <Button onClick={reReview} disabled={step === STEP.APPLYING}>
                        Reload Latest
                    </Button>
                </HorizontalStack>
            </VerticalStack>
        )
    }

    if (step === STEP.DONE) {
        return (
            <Box paddingBlockEnd="4">
                <Banner status="success" title="Fix applied successfully">
                    <p>The file was updated on the device.</p>
                </Banner>
            </Box>
        )
    }

    // STEP.FAILED
    return (
        <VerticalStack gap="3">
            <Banner status="critical" title="Couldn't apply the fix">
                <p>{describeErrorReason(errorReason)}</p>
            </Banner>
            <HorizontalStack gap="2">
                <Button onClick={reReview}>Reload Latest and Try Again</Button>
            </HorizontalStack>
        </VerticalStack>
    )
}

export default ConfigRemediationAction
