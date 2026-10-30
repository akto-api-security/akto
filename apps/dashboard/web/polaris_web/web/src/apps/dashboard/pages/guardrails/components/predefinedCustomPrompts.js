// Predefined "quick-fill" prompts for the Custom Guardrails "Detection instruction" field.
// Paste your finalized prompt text into the `prompt` field below for each entry.
// To add another predefined policy, append a new { id, label, prompt } object — no other file needs to change.
export const PREDEFINED_CUSTOM_PROMPTS = [
    {
        id: "unauthorized-destination",
        label: "Unauthorized destination policy",
        prompt: `PASTE PROMPT TEXT HERE`
    },
    {
        id: "excessive-data-access",
        label: "Excessive data access policy",
        prompt: `PASTE PROMPT TEXT HERE`
    },
    {
        id: "destructive-action-protection",
        label: "Destructive action protection policy",
        prompt: `PASTE PROMPT TEXT HERE`
    },
    {
        id: "default-customer-pii",
        label: "Default-Customer PII",
        prompt: `PASTE PROMPT TEXT HERE`
    }
];
