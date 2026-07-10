import { useState } from "react";
import {
    VerticalStack,
    Text,
    Box,
    Banner,
    Checkbox,
    TextField
} from "@shopify/polaris";
import OwaspTag from "../OwaspTag";

const ANOMALY_CATEGORIES = [
    "Statistical Anomalies",
    "Structural Anomalies",
    "Behavioral Anomalies"
];

export const AnomalyDetectionConfig = {
    number: 8,
    title: "Anomaly Detection",

    validate: () => {
        return { isValid: true, errorMessage: null };
    },

    getSummary: ({ enableAnomalyDetection }) => {
        return enableAnomalyDetection ? "Enabled" : null;
    }
};

const intSetter = (setter, min, max) => (val) => {
    const parsed = parseInt(val, 10);
    if (isNaN(parsed)) return;
    setter(Math.min(max, Math.max(min, parsed)));
};

const AnomalyDetectionStep = ({
    enableAnomalyDetection, setEnableAnomalyDetection,
    anomalyToolCallLimit, setAnomalyToolCallLimit,
    anomalyErrorLimit, setAnomalyErrorLimit,
}) => {
    const [expandedCategories, setExpandedCategories] = useState(() => {
        const initial = new Set();
        if (enableAnomalyDetection) initial.add("Statistical Anomalies");
        return initial;
    });

    const toggleCategory = (category) => {
        setExpandedCategories((prev) => {
            const next = new Set(prev);
            if (next.has(category)) next.delete(category);
            else next.add(category);
            return next;
        });
    };

    const renderCategoryContent = (category) => {
        if (category === "Statistical Anomalies") {
            return (
                <div onClick={(e) => e.stopPropagation()}>
                    <Box paddingBlockStart="4">
                        <VerticalStack gap="4">
                            <Checkbox
                                label="Enable anomaly detection"
                                checked={enableAnomalyDetection}
                                onChange={setEnableAnomalyDetection}
                                helpText="Detect and alert on abnormal tool call counts and error counts per session."
                            />
                            {enableAnomalyDetection && (
                                <Box paddingInlineStart="7">
                                    <VerticalStack gap="4">
                                        <TextField
                                            label="Tool call limit (per session)"
                                            type="number"
                                            value={anomalyToolCallLimit != null ? String(anomalyToolCallLimit) : ""}
                                            onChange={intSetter(setAnomalyToolCallLimit, 1, 100000)}
                                            helpText="Total tool calls allowed per session before triggering an anomaly."
                                            autoComplete="off"
                                        />
                                        <TextField
                                            label="Error limit (per session)"
                                            type="number"
                                            value={anomalyErrorLimit != null ? String(anomalyErrorLimit) : ""}
                                            onChange={intSetter(setAnomalyErrorLimit, 1, 100000)}
                                            helpText="Total errors (4xx/5xx) per session before triggering an anomaly."
                                            autoComplete="off"
                                        />
                                    </VerticalStack>
                                </Box>
                            )}
                        </VerticalStack>
                    </Box>
                </div>
            );
        }
        return (
            <Box paddingBlockStart="3">
                <Banner status="info" title="Coming soon">
                    <Text variant="bodyMd">This anomaly category is not yet available.</Text>
                </Banner>
            </Box>
        );
    };

    return (
        <VerticalStack gap="4">
            <Text variant="bodyMd" tone="subdued">
                Configure anomaly detection rules to identify unusual patterns in agent behavior, tool usage, and system metrics.
            </Text>
            <OwaspTag stepNumber={8} />

            <VerticalStack gap="2">
                {ANOMALY_CATEGORIES.map((category) => {
                    const alwaysExpanded = category === "Statistical Anomalies";
                    const isExpanded = alwaysExpanded || expandedCategories.has(category);
                    return (
                        <div
                            key={category}
                            onClick={alwaysExpanded ? undefined : () => toggleCategory(category)}
                            style={{ cursor: alwaysExpanded ? "default" : "pointer" }}
                        >
                            <Box
                                padding="3"
                                borderColor="border"
                                borderWidth="1"
                                borderRadius="2"
                                background="bg-surface"
                            >
                                <Text variant="headingSm">{category}</Text>
                                {isExpanded && renderCategoryContent(category)}
                            </Box>
                        </div>
                    );
                })}
            </VerticalStack>
        </VerticalStack>
    );
};

export default AnomalyDetectionStep;
