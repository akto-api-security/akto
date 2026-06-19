import { Tooltip, Checkbox } from "@shopify/polaris";

const TOOLTIP_TEMPLATE = "Automatically map this {noun} to relevant compliance frameworks (GDPR, HIPAA, PCI DSS, ISO 27001, etc.) using AI. This helps you understand which regulations and standards your guardrail policies help satisfy, making compliance audits and policy documentation easier.";

const LlmComplianceCheckbox = ({ noun = "rule", ...checkboxProps }) => (
  <Tooltip content={TOOLTIP_TEMPLATE.replace("{noun}", noun)}>
    <Checkbox
      label="Enable LLM-based compliance mapping"
      helpText="AI will suggest which compliance frameworks this rule relates to"
      {...checkboxProps}
    />
  </Tooltip>
);

export default LlmComplianceCheckbox;
