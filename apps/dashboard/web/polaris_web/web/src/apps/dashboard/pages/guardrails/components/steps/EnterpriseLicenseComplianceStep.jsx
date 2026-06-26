import { VerticalStack, Text, Checkbox, Box } from "@shopify/polaris";
import { ENTERPRISE_LICENSE_COMPLIANCE_CATEGORIES, enterpriseLicenseComplianceLabels } from "../enterpriseLicenseComplianceCatalog";

export const EnterpriseLicenseComplianceConfig = {
    number: 12,
    title: "Enterprise License Compliance Guardrails",

    validate: () => {
        return { isValid: true, errorMessage: null };
    },

    getSummary: ({ enterpriseLicenseComplianceCategories }) => {
        const labels = enterpriseLicenseComplianceLabels(enterpriseLicenseComplianceCategories);
        return labels.length > 0 ? labels.join(", ") : null;
    }
};

const EnterpriseLicenseComplianceStep = ({ enterpriseLicenseComplianceCategories = [], setEnterpriseLicenseComplianceCategories }) => {
    const toggle = (key, checked) => {
        if (checked) {
            setEnterpriseLicenseComplianceCategories([...enterpriseLicenseComplianceCategories, key]);
        } else {
            setEnterpriseLicenseComplianceCategories(enterpriseLicenseComplianceCategories.filter((k) => k !== key));
        }
    };

    return (
        <VerticalStack gap="4">
            <Text variant="bodyMd" tone="subdued">
                Block prompts and responses that would breach your LLM provider's acceptable-use policy, keeping your usage compliant so your enterprise license isn't put at risk.
            </Text>

            <VerticalStack gap="4">
                {ENTERPRISE_LICENSE_COMPLIANCE_CATEGORIES.map((c) => (
                    <Box key={c.key}>
                        <Checkbox
                            label={c.label}
                            checked={enterpriseLicenseComplianceCategories.includes(c.key)}
                            onChange={(checked) => toggle(c.key, checked)}
                            helpText={c.helpText}
                        />
                    </Box>
                ))}
            </VerticalStack>
        </VerticalStack>
    );
};

export default EnterpriseLicenseComplianceStep;
