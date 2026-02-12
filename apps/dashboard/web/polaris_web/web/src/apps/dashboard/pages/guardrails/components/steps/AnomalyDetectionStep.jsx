import {
    VerticalStack,
    Text,
    Box,
    Banner
} from "@shopify/polaris";

const ANOMALY_CATEGORIES = [
    "Statistical Anomalies",
    "Behavioral Anomalies",
    "Structural Anomalies"
];

export const AnomalyDetectionConfig = {
    number: 8,
    title: "Anomaly Detection",

    validate: () => {
        return { isValid: true, errorMessage: null };
    },

    getSummary: () => {
        return "Coming soon";
    }
};

const AnomalyDetectionStep = () => {
    return (
        <VerticalStack gap="4">
            <Text variant="headingMd">Anomaly Detection</Text>
            <Text variant="bodyMd" tone="subdued">
                Configure anomaly detection rules to identify unusual patterns in agent behavior, tool usage, and system metrics.
            </Text>

            <Banner title="Coming Soon" status="info">
                <Text variant="bodyMd">
                    Anomaly detection guardrails are currently under development.
                </Text>
            </Banner>

            <VerticalStack gap="2">
                {ANOMALY_CATEGORIES.map((category) => (
                    <Box key={category} padding="3" borderColor="border" borderWidth="1" borderRadius="2" background="bg-surface">
                        <Text variant="headingSm">{category}</Text>
                    </Box>
                ))}
            </VerticalStack>
        </VerticalStack>
    );
};

export default AnomalyDetectionStep;
