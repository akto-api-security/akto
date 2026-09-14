import { useState, useReducer } from "react";
import { produce } from "immer";
import { Modal, VerticalStack, HorizontalStack, Text } from "@shopify/polaris";
import guardrailApi from "../api";
import func from "@/util/func";
import values from "@/util/values";
import DateRangeFilter from "@/apps/dashboard/components/layouts/DateRangeFilter";

const toEpochSeconds = (date) => Math.floor(new Date(date).getTime() / 1000);

// "All time" is the natural default for a backfill — the whole point is catching traffic that
// predates this policy or guardrails being enabled, not just a recent window.
const getInitialDateRange = () => values.ranges.find(r => r.alias === "allTime") || values.ranges[values.ranges.length - 1];

/**
 * Schedules one background job per selected policy
 * (GuardrailPolicyReplayAction#startPolicyBackfillReplay) that re-runs traffic in the chosen
 * window through each policy's currently saved version and, for anything it now catches, records
 * a real guardrail activity backdated to the original traffic time.
 *
 * Fire-and-forget by design: every other AccountJob-backed feature in this app (AI Agent
 * Connector import, Copilot Studio sync, ...) is a toast pointing at the Account Jobs settings
 * page rather than an in-modal progress bar, so this follows the same convention instead of
 * introducing new progress-tracking UI.
 */
const BackfillReplayModal = ({ open, onClose, policies = [] }) => {
    const [dateRange, dispatchDateRange] = useReducer(
        produce((draft, action) => func.dateRangeReducer(draft, action)),
        getInitialDateRange()
    );
    const [submitting, setSubmitting] = useState(false);

    const handleClose = () => {
        if (submitting) return;
        onClose();
    };

    const handleSubmit = async () => {
        setSubmitting(true);
        const startTimestamp = toEpochSeconds(dateRange.period.since);
        const endTimestamp = toEpochSeconds(dateRange.period.until);
        try {
            const results = await Promise.allSettled(
                policies.map(p => guardrailApi.startGuardrailPolicyBackfillReplay({
                    policyName: p.name,
                    hexId: p.hexId,
                    startTimestamp,
                    endTimestamp,
                }))
            );
            const failedCount = results.filter(r => r.status === "rejected").length;
            const succeededCount = results.length - failedCount;

            if (succeededCount > 0) {
                func.setToast(true, false,
                    `Backfill started for ${succeededCount} polic${succeededCount > 1 ? "ies" : "y"} `
                    + `— check the Jobs page (Settings > Account Jobs) for progress`
                    + (failedCount > 0 ? `. ${failedCount} could not be started.` : ""));
            } else {
                func.setToast(true, true, "Could not start backfill");
            }
            onClose();
        } finally {
            setSubmitting(false);
        }
    };

    const count = policies.length;

    return (
        <Modal
            open={open}
            onClose={handleClose}
            title={`Backfill history for ${count} polic${count === 1 ? "y" : "ies"}`}
            primaryAction={{ content: "Start backfill", onAction: handleSubmit, loading: submitting, disabled: count === 0 }}
            secondaryActions={[{ content: "Cancel", onAction: handleClose, disabled: submitting }]}
        >
            <Modal.Section>
                <VerticalStack gap="4">
                    <Text variant="bodySm" color="subdued">
                        Re-runs traffic in this window through each policy's saved version and records a
                        real guardrail activity for anything it now catches, timestamped at the original
                        traffic time — useful for traffic that predates a policy or guardrails being
                        enabled.
                    </Text>
                    <HorizontalStack gap="2" blockAlign="center">
                        <Text variant="bodyMd" fontWeight="medium">Window</Text>
                        <DateRangeFilter
                            initialDispatch={dateRange}
                            dispatch={(dateObj) => dispatchDateRange({ type: "update", period: dateObj.period, title: dateObj.title, alias: dateObj.alias })}
                            disabled={submitting}
                        />
                    </HorizontalStack>
                </VerticalStack>
            </Modal.Section>
        </Modal>
    );
};

export default BackfillReplayModal;
