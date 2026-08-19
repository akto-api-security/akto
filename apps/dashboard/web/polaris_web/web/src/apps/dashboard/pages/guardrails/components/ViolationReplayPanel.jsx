import { useState, useRef, useEffect, useCallback } from "react";

import {
    Text,
    HorizontalStack,
    VerticalStack,
    Box,
    Button,
    Badge,
    Spinner,
    Divider
} from "@shopify/polaris";

import guardrailApi from '../api';
import Store from '../../../store';

// Accounts this panel is enabled for, while the comparison is still being validated against real
// traffic. Gated rather than shipped to everyone because a run costs real scanner calls and the
// counts are only meaningful once a policy has enough recorded violations to compare over.
const ENABLED_ACCOUNT_IDS = ['1726615470']; // nginx demo

// Cadence for reading run progress. The run itself is one server-side job; this only reads it.
const POLL_INTERVAL_MS = 2000;
// Stop polling eventually so a wedged run cannot spin forever.
const POLL_TIMEOUT_MS = 10 * 60 * 1000;
// Prompts longer than this are clamped until expanded.
const COLLAPSED_CHARS = 140;
// The list scrolls within this height rather than growing the panel unbounded.
const MISSED_LIST_MAX_HEIGHT = 360;

/** One prompt row: separated, clamped, expandable. */
const MissedPrompt = ({ prompt }) => {
    const [expanded, setExpanded] = useState(false);
    const text = prompt || "(no prompt text stored)";
    const isLong = text.length > COLLAPSED_CHARS;
    const shown = expanded || !isLong ? text : `${text.slice(0, COLLAPSED_CHARS)}…`;

    return (
        <Box
            paddingBlockStart="2"
            paddingBlockEnd="2"
            paddingInlineStart="3"
            paddingInlineEnd="3"
            background="bg-subdued"
            borderRadius="1"
            borderWidth="1"
            borderColor="border-subdued"
        >
            <VerticalStack gap="1">
                <Text variant="bodySm" color="subdued" breakWord>{shown}</Text>
                {isLong && (
                    <div>
                        <Button plain size="slim" onClick={() => setExpanded(!expanded)}>
                            {expanded ? "Show less" : "Show more"}
                        </Button>
                    </div>
                )}
            </VerticalStack>
        </Box>
    );
};

/**
 * Shows how many of a policy's recent violations the saved policy catches versus how many the
 * edited draft would catch, and which prompts the draft stopped catching.
 *
 * Reports two counts over the same events rather than judging each violation individually. Stored
 * payloads are anonymized before being persisted, so "would this one still be caught?" is often
 * unanswerable — the triggering text may be gone, and a rule needing 30 matches cannot fire on the
 * ~20 that survive. Comparing both policies over identical payloads makes that suppression
 * common-mode, so the difference stays meaningful even when both absolutes are depressed.
 *
 * One server-side run drives the whole comparison; this component only polls it.
 *
 * @param policyName name the violations were recorded under — the policy's *saved* name, since a
 *   guardrail event's filterId is the policy name
 * @param hexId saved policy id, used to load the baseline to compare against
 * @param buildPolicy returns the current form state as a backend policy payload
 */
const ViolationReplayPanel = ({ policyName, hexId, buildPolicy }) => {
    const activeAccount = Store(state => state.activeAccount);
    const [status, setStatus] = useState("idle"); // idle | running | done | error
    const [error, setError] = useState("");
    const [result, setResult] = useState(null);

    const timer = useRef(null);
    const stopped = useRef(false);

    const clearTimer = useCallback(() => {
        if (timer.current) {
            clearTimeout(timer.current);
            timer.current = null;
        }
    }, []);

    // Stop polling when the panel goes away, so a closed drawer does not keep hitting the server.
    useEffect(() => () => { stopped.current = true; clearTimer(); }, [clearTimer]);

    const poll = useCallback(async (runId, startedAt) => {
        if (stopped.current) return;
        try {
            const resp = await guardrailApi.pollPolicyReplay(runId);
            if (stopped.current) return;

            const r = resp?.replayResult;
            if (!r) throw new Error("No response from comparison run");

            setResult(r);

            if (r.status === "DONE") { setStatus("done"); return; }
            if (r.status === "FAILED") { setError(r.error || "Comparison failed"); setStatus("error"); return; }
            if (r.status === "EXPIRED") { setError("This comparison expired — run it again"); setStatus("error"); return; }
            if (Date.now() - startedAt > POLL_TIMEOUT_MS) {
                setError("Comparison is taking too long — try again");
                setStatus("error");
                return;
            }
            timer.current = setTimeout(() => poll(runId, startedAt), POLL_INTERVAL_MS);
        } catch (e) {
            if (stopped.current) return;
            setError(e?.message || "Could not read comparison progress");
            setStatus("error");
        }
    }, []);

    const run = async () => {
        stopped.current = false;
        clearTimer();
        setStatus("running");
        setError("");
        setResult(null);

        try {
            const resp = await guardrailApi.startPolicyReplay({
                policy: buildPolicy(), policyName, hexId
            });
            const runId = resp?.replayResult?.runId;
            if (!runId) throw new Error("Could not start comparison");
            poll(runId, Date.now());
        } catch (e) {
            setError(e?.message || "Could not start comparison");
            setStatus("error");
        }
    };

    // After the hooks, never before: bailing earlier would change the hook count between renders.
    if (!ENABLED_ACCOUNT_IDS.includes(String(activeAccount))) {
        return null;
    }

    const current = result?.currentDetected || 0;
    const modified = result?.modifiedDetected || 0;
    const compared = result?.compared || 0;
    const missed = result?.missedByDraft || [];
    const delta = modified - current;
    const running = status === "running";

    return (
        <Box padding="5" borderBlockEndWidth="1" borderColor="border-subdued">
            <VerticalStack gap="3">
                <HorizontalStack align="space-between" blockAlign="center">
                    <Text variant="headingMd" as="h3" fontWeight="semibold">Impact on recent violations</Text>
                    <Button size="slim" onClick={run} loading={running} disabled={running}>
                        {status === "idle" ? "Compare" : "Re-compare"}
                    </Button>
                </HorizontalStack>

                {status === "idle" && (
                    <Text variant="bodySm" color="subdued">
                        Re-run this policy's recent violations through both the saved policy and your
                        changes, and compare how many each catches.
                    </Text>
                )}

                {status === "error" && <Text variant="bodySm" color="critical">{error}</Text>}

                {(running || status === "done") && (
                    <VerticalStack gap="4">
                        <HorizontalStack gap="5" blockAlign="center">
                            <VerticalStack gap="1">
                                <Text variant="bodySm" color="subdued">Saved policy</Text>
                                <Text variant="headingLg" as="p">{current}</Text>
                            </VerticalStack>
                            <VerticalStack gap="1">
                                <Text variant="bodySm" color="subdued">Your changes</Text>
                                <Text variant="headingLg" as="p">{modified}</Text>
                            </VerticalStack>
                            {!running && compared > 0 && (
                                <Badge status={delta > 0 ? "success" : delta < 0 ? "critical" : "info"}>
                                    {delta === 0 ? "no change" : `${delta > 0 ? "+" : ""}${delta} detected`}
                                </Badge>
                            )}
                            {running && <Spinner size="small" />}
                        </HorizontalStack>

                        {!running && compared > 0 && result?.baselineFromCache && (
                            <Text variant="bodySm" color="subdued">
                                Saved-policy result reused from the last comparison
                            </Text>
                        )}

                        {!running && compared === 0 && (
                            <Text variant="bodySm" color="subdued">
                                No recorded violations could be compared for this policy.
                            </Text>
                        )}

                        {missed.length > 0 && (
                            <VerticalStack gap="3">
                                <Divider />
                                <Text variant="bodySm" fontWeight="semibold">
                                    {`No longer detected by your changes (${missed.length})`}
                                </Text>
                                {/* Own scroll container: 30+ rows would otherwise grow past the
                                    panel and push the playground below it off-screen. */}
                                <div
                                    style={{
                                        maxHeight: MISSED_LIST_MAX_HEIGHT,
                                        overflowY: 'auto',
                                        paddingRight: 4
                                    }}
                                >
                                    <VerticalStack gap="2">
                                        {missed.map((row, i) => (
                                            <MissedPrompt key={row.id || i} prompt={row.prompt} />
                                        ))}
                                    </VerticalStack>
                                </div>
                            </VerticalStack>
                        )}
                    </VerticalStack>
                )}
            </VerticalStack>
        </Box>
    );
};

export default ViolationReplayPanel;
