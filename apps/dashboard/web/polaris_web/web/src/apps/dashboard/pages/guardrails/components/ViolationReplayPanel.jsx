import { useState, useRef, useEffect, useCallback } from "react";

import {
    Text,
    HorizontalStack,
    VerticalStack,
    Box,
    Button,
    Badge,
    Tooltip,
    ButtonGroup
} from "@shopify/polaris";

import InfoTooltipIcon from '../../../components/shared/InfoTooltipIcon';
import { REPLAY_SOURCE_TABS } from '../utils';
import PolicyDiffModal from './PolicyDiffModal';
import guardrailApi from '../api';
import Store from '../../../store';

const ENABLED_ACCOUNT_IDS = [];

// Fields that cannot move a verdict, so never count as a change.
const IGNORED_FIELDS = [
    // Step 1: naming and messaging only
    "name", "blockedMessage", "description",
    // Fetched option lists and step-visit flags, not policy content
    "mcpServers", "agentServers", "browserLlmServers",
    "serverScopeLeftDirty", "userScopeLeftDirty"
];

// Key-sorted and Set-aware: JSON.stringify flattens every Set to "{}".
const stableStringify = (value) => {
    if (value instanceof Set) return `Set[${[...value].map(stableStringify).sort().join(",")}]`;
    if (value instanceof Map) {
        return `Map[${[...value.entries()].map(([k, v]) => `${stableStringify(k)}:${stableStringify(v)}`).sort().join(",")}]`;
    }
    if (Array.isArray(value)) return `[${value.map(stableStringify).join(",")}]`;
    if (value && typeof value === "object") {
        return `{${Object.keys(value).sort()
            .filter(k => value[k] !== undefined)
            .map(k => `${JSON.stringify(k)}:${stableStringify(value[k])}`)
            .join(",")}}`;
    }
    return JSON.stringify(value === undefined ? null : value);
};

// Form state, not the backend payload: some toggles never reach it.
const detectionSignature = (policyState) => {
    const rules = { ...(policyState || {}) };
    IGNORED_FIELDS.forEach(k => delete rules[k]);
    return stableStringify(rules);
};

// How often to read progress; the run itself is server-side.
const POLL_INTERVAL_MS = 2000;
// So a wedged run cannot poll forever.
const POLL_TIMEOUT_MS = 10 * 60 * 1000;
/**
 * Counts how many recent events the saved policy catches versus the draft.
 *
 * Two counts over identical payloads, so capture-time anonymization cancels out of the difference.
 *
 * @param policyName the policy's *saved* name — violations join by name
 * @param hexId saved policy id, used to load the baseline
 * @param buildPolicy current form state as a backend policy payload
 * @param policyState raw form state, used to detect edits
 * @param seedVersion bumped when the form is loaded from the saved policy
 * @param isNewPolicy no saved policy to compare against, so report one count, not a difference
 */
const ViolationReplayPanel = ({ policyName, hexId, buildPolicy, policyState, seedVersion = 0,
    isNewPolicy = false }) => {
    const activeAccount = Store(state => state.activeAccount);
    const [status, setStatus] = useState("idle"); // idle | running | done | error
    // VIOLATIONS = recorded violations. TRACES = recent agent traffic.
    // Violations join to a policy by name, so a new policy has none — only traffic can be replayed.
    const [source, setSource] = useState(isNewPolicy ? "TRACES" : "VIOLATIONS");
    const [error, setError] = useState("");
    const [detailsOpen, setDetailsOpen] = useState(false);
    // Sticky: switching sample clears the counts but must keep the tabs.
    const [hasCompared, setHasCompared] = useState(false);
    const [result, setResult] = useState(null);

    const timer = useRef(null);
    const stopped = useRef(false);

    // The parent seeds the form in an effect, so re-baseline on each seed.
    const signature = detectionSignature(policyState);
    const latestSignature = useRef(signature);
    latestSignature.current = signature;
    // State, not a ref: the button must re-render with it.
    const [savedSignature, setSavedSignature] = useState(null);
    // This effect first runs before the parent seeds the form, so its first reading is a blank
    // policy — the reference for "any rule configured at all", which a preset satisfies on arrival.
    const blankSignature = useRef(null);
    useEffect(() => {
        if (blankSignature.current === null) blankSignature.current = latestSignature.current;
        setSavedSignature(latestSignature.current);
    }, [seedVersion]);
    const hasDetectionChanges = savedSignature !== null && signature !== savedSignature;
    const hasAnyRule = blankSignature.current !== null && signature !== blankSignature.current;

    const clearTimer = useCallback(() => {
        if (timer.current) {
            clearTimeout(timer.current);
            timer.current = null;
        }
    }, []);

    // A closed panel must stop hitting the server.
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

    // Clear rather than show stale counts under a different tab.
    const changeSource = (next) => {
        setSource(next);
        setStatus("idle");
        setResult(null);
    };

    const run = async () => {
        setHasCompared(true);
        stopped.current = false;
        clearTimer();
        setStatus("running");
        setError("");
        setResult(null);

        try {
            const resp = await guardrailApi.startPolicyReplay({
                policy: buildPolicy(), policyName, hexId, source
            });
            const runId = resp?.replayResult?.runId;
            if (!runId) throw new Error("Could not start comparison");
            poll(runId, Date.now());
        } catch (e) {
            setError(e?.message || "Could not start comparison");
            setStatus("error");
        }
    };

    // After the hooks: bailing earlier would change the hook count.
    if (!ENABLED_ACCOUNT_IDS.includes(String(activeAccount))) {
        return null;
    }

    const current = result?.currentDetected || 0;
    const modified = result?.modifiedDetected || 0;
    const compared = result?.compared || 0;
    const delta = modified - current;
    const running = status === "running";
    const idle = status === "idle" && !hasCompared;
    // Editing lists lost detections; a new policy has no baseline, so it lists caught ones instead.
    const rows = isNewPolicy
        ? (result?.gainedByDraft || []).map(g => ({ ...g, wasDetected: false, nowDetected: true }))
        : (result?.missedByDraft || []).map(m => ({ ...m, wasDetected: true, nowDetected: false }));
    // A zero delta can still hide prompts that moved both ways.
    // New-policy detail is only ever the listed rows, so never offer an empty table.
    const hasDiff = isNewPolicy ? rows.length > 0 : (rows.length > 0 || delta !== 0);
    // Naming or describing a policy is not a guardrail, so it cannot enable the run on its own.
    const named = !!(policyName || "").trim();
    const canRun = isNewPolicy ? (named && hasAnyRule) : hasDetectionChanges;
    const blockedReason = isNewPolicy
        ? (!named
            ? "Name your policy to run an impact analysis."
            : "Configure a guardrail rule to run an impact analysis.")
        : "Change a guardrail rule to compare it against the saved policy.";

    const compareButton = (
        <Button size="slim" onClick={run} loading={running} disabled={running || !canRun}>
            {hasCompared ? (isNewPolicy ? "Re-run" : "Re-compare") : (isNewPolicy ? "Run" : "Compare")}
        </Button>
    );

    return (
        <Box padding="5" borderBlockEndWidth="1" borderColor="border-subdued">
            <VerticalStack gap="3">
                <HorizontalStack align="space-between" blockAlign="center">
                    <HorizontalStack gap="1" blockAlign="center">
                        <Text variant="headingMd" as="h3" fontWeight="semibold">
                            {isNewPolicy ? "Impact analysis" : "Change impact analysis"}
                        </Text>
                        <InfoTooltipIcon content={isNewPolicy
                            ? "Runs recent agent traffic through this policy to show how much of it would be caught."
                            : "Re-runs a recent sample through the saved policy and your unsaved draft, then shows which prompts changed verdict."} />
                    </HorizontalStack>
                    {/* Renaming or rewording a message cannot move a verdict. */}
                    {canRun ? compareButton : (
                        <Tooltip content={blockedReason}>
                            <div>{compareButton}</div>
                        </Tooltip>
                    )}
                </HorizontalStack>

                {/* Tabs only mean something once there are counts, and only traffic applies to a new policy. */}
                {!idle && !isNewPolicy && (
                    <ButtonGroup segmented>
                        {REPLAY_SOURCE_TABS.map(tab => (
                            <Button key={tab.id} size="slim" pressed={source === tab.id} disabled={running}
                                onClick={() => changeSource(tab.id)}>
                                {tab.content}
                            </Button>
                        ))}
                    </ButtonGroup>
                )}

                {/* Only after a sample switch; the first idle state stays bare. */}
                {status === "idle" && hasCompared && (
                    <Text variant="bodySm" color="subdued">
                        {source === "TRACES"
                            ? "Re-run recent agent traffic through both the saved policy and your changes, and compare how many each catches."
                            : "Re-run this policy's recent violations through both the saved policy and your changes, and compare how many each catches."}
                    </Text>
                )}

                {status === "error" && <Text variant="bodySm" color="critical">{error}</Text>}

                {(running || status === "done") && (
                    <VerticalStack gap="4">
                        {/* Progress lives in the Re-compare button alone. */}
                        <HorizontalStack gap="5" blockAlign="center">
                            {!isNewPolicy && (
                                <VerticalStack gap="1">
                                    <Text variant="bodySm" color="subdued">Saved policy</Text>
                                    <Text variant="headingLg" as="p">{current}</Text>
                                </VerticalStack>
                            )}
                            <VerticalStack gap="1">
                                <Text variant="bodySm" color="subdued">
                                    {isNewPolicy ? "Would be caught" : "Your changes"}
                                </Text>
                                {/* Badge sits with the number it qualifies. */}
                                <HorizontalStack gap="2" blockAlign="center">
                                    <Text variant="headingLg" as="p">{modified}</Text>
                                    {isNewPolicy && !running && compared > 0 && (
                                        <Text variant="bodySm" color="subdued">{`of ${compared} compared`}</Text>
                                    )}
                                    {!running && !isNewPolicy && compared > 0 && (
                                        <Badge status={delta > 0 ? "success" : delta < 0 ? "critical" : "info"}>
                                            {delta === 0 ? "no change" : `${delta > 0 ? "+" : ""}${delta}`}
                                        </Badge>
                                    )}
                                </HorizontalStack>
                            </VerticalStack>
                        </HorizontalStack>

                        {/* Names the sample, so the count is not read as an industry benchmark. */}
                        {isNewPolicy && !running && compared > 0 && (
                            <Text variant="bodySm" color="subdued">
                                Your own agent traffic, from the last 30 days.
                            </Text>
                        )}

                        {!running && status === "done" && compared > 0 && (
                            <Box>
                                <Button size="slim" onClick={() => setDetailsOpen(true)} disabled={!hasDiff}>
                                    View details
                                </Button>
                            </Box>
                        )}

                        {!running && compared > 0 && result?.baselineFromCache && (
                            <Text variant="bodySm" color="subdued">
                                Saved-policy result reused from the last comparison
                            </Text>
                        )}

                        {!running && compared === 0 && (
                            <Text variant="bodySm" color="subdued">
                                {isNewPolicy
                                    ? "No recent traffic could be analysed."
                                    : source === "TRACES"
                                        ? "No recent traffic could be compared."
                                        : "No recorded violations could be compared for this policy."}
                            </Text>
                        )}

                    </VerticalStack>
                )}
            </VerticalStack>

            <PolicyDiffModal
                open={detailsOpen}
                onClose={() => setDetailsOpen(false)}
                source={source}
                onSourceChange={changeSource}
                isNewPolicy={isNewPolicy}
                result={result}
                rows={rows}
            />
        </Box>
    );
};

export default ViolationReplayPanel;
