import { useState, useEffect, useCallback } from "react";
import { useNavigate } from "react-router-dom";
import { Modal, VerticalStack, Checkbox, Text, Box, Scrollable } from "@shopify/polaris";
import func from "@/util/func";
import {
    buildDeniedTopicsFromTopics,
    createOrAppendTopicGuardrail,
    TOPIC_GUARDRAIL_POLICY_NAME,
} from "../topicGuardrailUtils";

const GUARDRAIL_POLICIES_PATH = "/dashboard/guardrails/policies";

// Shared topic-picker modal used from the LLM Traces and Endpoints pages.
// Lets the user pick conversation topics, then either creates a fresh canonical
// topic-guardrail policy (prefilled on the Guardrails page) or appends the
// selected topics to the existing one.
//
// Props:
//   open       - boolean, controls visibility
//   onClose    - () => void
//   topics     - string[] of topic names to choose from
//   scope      - { sessionId } (Traces) or { userName, deviceId } (Endpoints)
//   dateRange  - { startTime, endTime } in epoch seconds
export default function CreateTopicGuardrailModal({ open, onClose, topics = [], scope, dateRange }) {
    const navigate = useNavigate();
    const [selected, setSelected] = useState([]);
    const [loading, setLoading] = useState(false);

    // Reset selection whenever the modal opens with a fresh topic set.
    useEffect(() => {
        if (open) {
            setSelected([]);
            setLoading(false);
        }
    }, [open]);

    const toggleTopic = useCallback((topic) => {
        setSelected(prev =>
            prev.includes(topic) ? prev.filter(t => t !== topic) : [...prev, topic]
        );
    }, []);

    const handleContinue = useCallback(async () => {
        if (selected.length === 0) return;
        setLoading(true);
        try {
            const deniedTopics = await buildDeniedTopicsFromTopics(selected, scope, dateRange);
            const result = await createOrAppendTopicGuardrail(deniedTopics);

            if (result.action === "create") {
                onClose();
                navigate(GUARDRAIL_POLICIES_PATH, {
                    state: { topicGuardrailPrefill: result.prefill },
                });
                return;
            }

            // append
            if (result.addedCount > 0) {
                func.setToast(true, false, `Added ${result.addedCount} topic${result.addedCount > 1 ? "s" : ""} to ${TOPIC_GUARDRAIL_POLICY_NAME}`);
            } else {
                func.setToast(true, false, `Already in ${TOPIC_GUARDRAIL_POLICY_NAME}`);
            }
            onClose();
        } catch (error) {
            func.setToast(true, true, "Failed to create guardrail from topics");
        } finally {
            setLoading(false);
        }
    }, [selected, scope, dateRange, navigate, onClose]);

    return (
        <Modal
            open={open}
            onClose={onClose}
            title="Create blocking guardrail from topics"
            primaryAction={{
                content: "Continue",
                onAction: handleContinue,
                disabled: selected.length === 0,
                loading,
            }}
            secondaryActions={[{ content: "Cancel", onAction: onClose, disabled: loading }]}
        >
            <Modal.Section>
                <VerticalStack gap="3">
                    <Text variant="bodyMd" color="subdued">
                        Select the conversation topics you want to block. A blocking guardrail will be created or updated with sample phrases pulled from matching traffic.
                    </Text>
                    <Scrollable style={{ maxHeight: "300px" }}>
                        <Box paddingInlineEnd="2">
                            <VerticalStack gap="2">
                                {topics.map(topic => (
                                    <Checkbox
                                        key={topic}
                                        label={func.toSentenceCase(topic)}
                                        checked={selected.includes(topic)}
                                        onChange={() => toggleTopic(topic)}
                                        disabled={loading}
                                    />
                                ))}
                            </VerticalStack>
                        </Box>
                    </Scrollable>
                </VerticalStack>
            </Modal.Section>
        </Modal>
    );
}
