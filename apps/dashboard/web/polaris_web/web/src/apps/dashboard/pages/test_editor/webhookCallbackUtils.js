/**
 * Utils for webhook callback (e.g. SSRF) polling and status in the test editor.
 */
import func from "@/util/func"

export const CALLBACK_STATUS_MESSAGES = {
    pending: "Waiting for webhook callback...",
    hit: "Webhook callback observed",
    not_hit: "No webhook callback observed yet",
}

export const CALLBACK_POLL_CONFIG = {
    maxAttempts: 24,
    pollIntervalMs: 5000,
}

export const CALLBACK_NO_TOKENS_MESSAGE = "No webhook callback tokens available for this run"

export const PLAYGROUND_POLL_CONFIG = {
    maxAttempts: 100,
    pollIntervalMs: 3000,
}

export const isCallbackTest = (testResult) =>
    testResult?.testingRunResult?.testSubType?.toLowerCase().includes("ssrf")

export const normalizeCallbackUuids = (resp) =>
    Array.isArray(resp?.callbackUuids) && resp.callbackUuids.length > 0 ? resp.callbackUuids : []

export const getCallbackStatusMessage = (status) =>
    status ? (CALLBACK_STATUS_MESSAGES[status] || "No webhook callback observed") : null

/**
 * Starts polling for webhook callback hit. Returns a stop() function to clear the interval.
 * @param {{ uuids: string[], fetchStatus: (uuids: string[]) => Promise<{ callbackHit?: boolean }>, onHit: () => void, onNotHit: () => void }}
 */
export function startCallbackPolling({ uuids, fetchStatus, onHit, onNotHit }) {
    let attempts = 0
    const intervalId = setInterval(async () => {
        if (attempts >= CALLBACK_POLL_CONFIG.maxAttempts) {
            clearInterval(intervalId)
            onNotHit()
            return
        }
        try {
            const statusResp = await fetchStatus(uuids)
            if (statusResp?.callbackHit === true) {
                clearInterval(intervalId)
                onHit()
                return
            }
        } catch (_) {}
        attempts++
    }, CALLBACK_POLL_CONFIG.pollIntervalMs)
    return () => {
        clearInterval(intervalId)
    }
}

/** Returns error message if callback check is not allowed, null otherwise. */
export const getCallbackCheckError = (testResult, callbackUuids) => {
    const uuids = callbackUuids || []
    if (!testResult || uuids.length === 0) return CALLBACK_NO_TOKENS_MESSAGE
    return null
}

/** Returns test result with vulnerable set to true, or same result if unchanged. */
export const markTestResultVulnerable = (prevResult) =>
    prevResult?.testingRunResult
        ? { ...prevResult, testingRunResult: { ...prevResult.testingRunResult, vulnerable: true } }
        : prevResult

/**
 * Polls playground status until COMPLETED or max attempts. Returns stop() to clear the interval.
 * @param {{ playgroundHexId: string, fetchStatus: (id: string) => Promise<{ testingRunPlaygroundStatus?: string }>, onComplete: (result: any) => void, onTimeout: () => void }}
 */
export function startPlaygroundPolling({ playgroundHexId, fetchStatus, onComplete, onTimeout }) {
    let attempts = 0
    const intervalId = setInterval(async () => {
        if (attempts >= PLAYGROUND_POLL_CONFIG.maxAttempts) {
            clearInterval(intervalId)
            onTimeout()
            return
        }
        try {
            const result = await fetchStatus(playgroundHexId)
            if (result?.testingRunPlaygroundStatus === "COMPLETED") {
                clearInterval(intervalId)
                onComplete(result)
                return
            }
        } catch (err) {
            console.error("Error fetching updateResult:", err)
        }
        attempts++
    }, PLAYGROUND_POLL_CONFIG.pollIntervalMs)
    return () => clearInterval(intervalId)
}

export const SEVERITY_CLASS = { HIGH: "bg-critical", MEDIUM: "bg-caution", LOW: "bg-info" }

export const getResultColor = (testResult) => {
    if (!testResult) return "bg"
    if (!testResult.testingRunResult.vulnerable) return "bg-success"
    const status = func.getRunResultSeverity(testResult.testingRunResult, testResult.subCategoryMap)?.toUpperCase()
    return SEVERITY_CLASS[status] || "bg"
}

export const getResultDescription = ({ testResult, callbackStatus, isChatBotOpen, mapLabel, getDashboardCategory }) => {
    if (isChatBotOpen) return "Chat with the agent"
    if (!testResult) return `${mapLabel('Run test', getDashboardCategory())} to see Results`
    if (isCallbackTest(testResult) && callbackStatus) return getCallbackStatusMessage(callbackStatus)
    if (testResult.testingRunResult.vulnerable) {
        const status = func.getRunResultSeverity(testResult.testingRunResult, testResult.subCategoryMap)
        return func.toSentenceCase(status) + " vulnerability found"
    }
    return "No vulnerability found"
}
