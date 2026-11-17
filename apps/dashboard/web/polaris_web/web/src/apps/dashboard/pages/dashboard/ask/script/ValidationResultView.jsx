import { Badge, Banner, HorizontalStack, Spinner, Text, TextField, VerticalStack } from "@shopify/polaris"
import observeTransform from "../../../observe/transform"

function parseReplay(message) {
    if (!message) return null
    try {
        const parsed = JSON.parse(message)
        const headers = (h) => {
            if (!h) return ""
            try { return JSON.stringify(typeof h === "string" ? JSON.parse(h) : h, null, 2) } catch (e) { return String(h) }
        }
        return {
            request: `${parsed.request?.method || ""} ${parsed.request?.url || ""}\n${headers(parsed.request?.headers)}\n\n${parsed.request?.body || ""}`,
            response: `HTTP ${parsed.response?.statusCode ?? ""}\n${headers(parsed.response?.headers)}\n\n${parsed.response?.body || ""}`,
        }
    } catch (e) {
        return { request: message, response: "" }
    }
}

function CodeField({ label, value, lines = 6 }) {
    return <TextField label={label} value={value || ""} multiline={lines} readOnly monospaced autoComplete="off" />
}

function PhaseDiagnostics({ label, diagnostics }) {
    if (!diagnostics) return null
    if (!diagnostics.runs) {
        return (
            <Banner status="warning" title={`${label} script did not run`}>
                {diagnostics.error ? <Text as="p">{diagnostics.error}</Text> : <Text as="p">Scripts run only when the TEST_PRE_SCRIPT feature is enabled for the account.</Text>}
            </Banner>
        )
    }
    return (
        <VerticalStack gap="2">
            <HorizontalStack gap="2" blockAlign="center">
                <Text as="h4" variant="headingSm">{label} script</Text>
                {diagnostics.error ? <Badge status="critical">Threw an error</Badge> : <Badge status="success">Finished</Badge>}
                {diagnostics.runs > 1 ? <Badge>{`Ran ${observeTransform.formatNumberWithCommas(diagnostics.runs)} times`}</Badge> : null}
            </HorizontalStack>
            {diagnostics.error ? <Banner status="critical"><Text as="p" breakWord>{diagnostics.error}</Text></Banner> : null}
            <CodeField label={`print() output${diagnostics.logsTruncated ? " (cut)" : ""}`} value={diagnostics.logs || "(no output)"} />
            <CodeField label={`Before the script${diagnostics.snapshotsTruncated ? " (cut)" : ""}`} value={diagnostics.before} />
            <CodeField label="After the script" value={diagnostics.after ?? "(none: the script threw)"} />
        </VerticalStack>
    )
}

// The outcome of one validation. Missing diagnostics read "unavailable", never "no errors".
export default function ValidationResultView({ result, running }) {
    if (running) {
        return (
            <HorizontalStack gap="2" blockAlign="center">
                <Spinner size="small" accessibilityLabel="Replay running" />
                <Text as="p" color="subdued">Replaying… scripts that poll can take a few minutes.</Text>
            </HorizontalStack>
        )
    }
    if (!result) return null
    const replay = parseReplay(result.replayMessage)
    return (
        <VerticalStack gap="3">
            <HorizontalStack gap="2" blockAlign="center">
                <Badge status="success">Replay finished</Badge>
                {result.statusCode != null ? <Text as="span" color="subdued">Final status {result.statusCode}</Text> : null}
            </HorizontalStack>
            {(result.warnings || []).map((w) => <Banner key={w} status="warning"><Text as="p">{w}</Text></Banner>)}
            {result.diagnosticsAvailable ? (
                <VerticalStack gap="4">
                    <PhaseDiagnostics label="Pre-request" diagnostics={result.diagnostics?.pre} />
                    <PhaseDiagnostics label="Post-request" diagnostics={result.diagnostics?.post} />
                </VerticalStack>
            ) : (
                <Banner status="info" title="Script errors and logs unavailable">
                    <Text as="p">{result.diagnosticsUnavailableReason || "This module did not report them."}</Text>
                </Banner>
            )}
            {replay ? (
                <VerticalStack gap="2">
                    <Text as="h4" variant="headingSm">What was sent and received{result.replayMessageTruncated ? " (cut)" : ""}</Text>
                    <CodeField label="Request (after the pre-request script)" value={replay.request} lines={8} />
                    <CodeField label="Response (after the post-request script)" value={replay.response} lines={8} />
                </VerticalStack>
            ) : null}
        </VerticalStack>
    )
}
