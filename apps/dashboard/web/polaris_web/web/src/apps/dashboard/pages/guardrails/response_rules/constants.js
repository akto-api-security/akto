export const PAGE_SIZE = 20;

export const CONDITION_KIND = { MULTI: "multi", POLICY: "policy", TEXT: "text", HOURS: "hours" };

export const SEVERITY_OPTIONS = [
    { label: "Critical", value: "CRITICAL" },
    { label: "High", value: "HIGH" },
    { label: "Medium", value: "MEDIUM" },
    { label: "Low", value: "LOW" },
];

export const ACCOUNT_TYPE_OPTIONS = [
    { label: "Personal", value: "personal" },
    { label: "Corporate", value: "corporate" },
];

export const SURFACE_OPTIONS = [
    { label: "Agents & CLIs", value: "agents" },
    { label: "MCP servers", value: "mcp" },
    { label: "Browser AI apps", value: "browser" },
];

export const CONDITION_FIELDS = [
    { field: "severity", label: "Severity", kind: CONDITION_KIND.MULTI, options: SEVERITY_OPTIONS },
    { field: "policy", label: "Policy", kind: CONDITION_KIND.POLICY },
    { field: "accountType", label: "Account type", kind: CONDITION_KIND.MULTI, options: ACCOUNT_TYPE_OPTIONS },
    { field: "surface", label: "Surface", kind: CONDITION_KIND.MULTI, options: SURFACE_OPTIONS },
    { field: "agent", label: "Agent or app", kind: CONDITION_KIND.TEXT },
    { field: "unassignedHours", label: "Unassigned for (hours)", kind: CONDITION_KIND.HOURS },
];

export const PRIORITY_OPTIONS = [
    { label: "Highest", value: "HIGHEST" },
    { label: "High", value: "HIGH" },
    { label: "Medium", value: "MEDIUM" },
    { label: "Low", value: "LOW" },
];

export const TICKET_DESTINATIONS = [
    { label: "Jira", value: "JIRA" },
    { label: "ServiceNow", value: "SERVICENOW" },
];

export const DUE_OPTIONS = [
    { label: "No due date", value: "0" },
    { label: "24 hours", value: "24" },
    { label: "3 days", value: "72" },
    { label: "1 week", value: "168" },
];

export const ALERT_CHANNELS = [
    { label: "Slack", value: "slack", destination: "SLACK", live: true },
    { label: "Email", value: "email", live: true },
    { label: "Webhook", value: "webhook", destination: "WEBHOOK", live: true },
];

export const IGNORE_REASONS = [
    { label: "False positive", value: "fp" },
    { label: "Accepted risk", value: "risk" },
    { label: "Expected behavior", value: "expected" },
    { label: "Duplicate", value: "dup" },
];

export const IGNORE_EXPIRY_OPTIONS = [
    { label: "Never", value: "0" },
    { label: "7 days", value: "7" },
    { label: "30 days", value: "30" },
    { label: "90 days", value: "90" },
];

export const ACTION_TYPES = [
    { type: "CREATE_TICKET", label: "Create ticket", help: "Open a Jira or ServiceNow ticket", defaults: { destination: "JIRA", priority: "HIGH", includeEvidence: true, syncStatus: true } },
    { type: "ASSIGN", label: "Assign", help: "Assign the violation to a person", live: true, defaults: { assignee: "", dueHours: "24", notifySlack: false } },
    { type: "ALERT", label: "Send alert", help: "Alert a channel when it happens", live: true, defaults: { channels: ["slack"], slackWebhookId: "", webhookId: "", emails: "" } },
    { type: "IGNORE", label: "Ignore", help: "Close it automatically with a reason", live: true, defaults: { reason: "expected", expiryDays: "0" } },
    { type: "COACH_EMPLOYEE", label: "Send coaching message", help: "Explain the policy to the employee", defaults: { message: "" } },
    { type: "NOTIFY_MANAGER", label: "Notify manager", help: "Escalate repeat events to their manager", defaults: { afterEvents: "3", windowDays: "7" } },
];

export const DESTINATION_PAGES = {
    JIRA: "/dashboard/settings/integrations/jira",
    SERVICENOW: "/dashboard/settings/integrations/servicenow",
    SLACK: "/dashboard/settings/integrations/slack",
    WEBHOOK: "/dashboard/settings/integrations/webhooks",
};

export const RULE_NAME_MAX = 120;
