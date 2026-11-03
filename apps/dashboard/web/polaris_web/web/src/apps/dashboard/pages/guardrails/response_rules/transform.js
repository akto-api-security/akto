import func from "@/util/func";
import {
    ACCOUNT_TYPE_OPTIONS, ACTION_TYPES, ALERT_CHANNELS, CONDITION_FIELDS, DUE_OPTIONS, IGNORE_EXPIRY_OPTIONS, IGNORE_REASONS,
    PRIORITY_OPTIONS, RULE_NAME_MAX, SEVERITY_OPTIONS, SURFACE_OPTIONS, TICKET_DESTINATIONS,
} from "./constants";

const labelOf = (options, value) => options.find((option) => option.value === value)?.label || value;
const listOf = (values) => values.join(", ");

const VALUE_LABELS = {
    severity: (value) => labelOf(SEVERITY_OPTIONS, value),
    accountType: (value) => labelOf(ACCOUNT_TYPE_OPTIONS, value),
    surface: (value) => labelOf(SURFACE_OPTIONS, value),
};

function conditionText(condition) {
    const field = CONDITION_FIELDS.find((entry) => entry.field === condition.field);
    if (!field) return "";
    if (condition.field === "unassignedHours") return `Unassigned for ${condition.values[0]} hours`;
    const format = VALUE_LABELS[condition.field] || ((value) => value);
    const values = (condition.values || []).map(format);
    return `${field.label} is ${values.length > 1 ? "any of " : ""}${listOf(values)}`;
}

function actionText(action) {
    const config = action.config || {};
    switch (action.type) {
        case "CREATE_TICKET": return `Create ${labelOf(TICKET_DESTINATIONS, config.destination)} ticket (${labelOf(PRIORITY_OPTIONS, config.priority)} priority)`;
        case "ASSIGN": return `Assign to ${config.assignee || "nobody yet"}${config.dueHours && config.dueHours !== "0" ? `, due in ${labelOf(DUE_OPTIONS, config.dueHours)}` : ""}`;
        case "ALERT": {
            const channels = (config.channels || []).map((channel) => labelOf(ALERT_CHANNELS, channel));
            return `Alert ${listOf(channels)}${config.slackChannel ? ` ${config.slackChannel}` : ""}`;
        }
        case "IGNORE": return `Ignore with reason ${labelOf(IGNORE_REASONS, config.reason)}${config.expiryDays && config.expiryDays !== "0" ? `, expires in ${labelOf(IGNORE_EXPIRY_OPTIONS, config.expiryDays)}` : ""}`;
        case "COACH_EMPLOYEE": return "Send coaching message to employee";
        case "NOTIFY_MANAGER": return `Notify manager after ${config.afterEvents} events in ${config.windowDays} days`;
        default: return "";
    }
}

export function defaultAction(type) {
    const definition = ACTION_TYPES.find((entry) => entry.type === type);
    return { type, config: { ...definition.defaults } };
}

const transform = {
    whenText: (rule) => (rule.conditions || []).map(conditionText).filter(Boolean).join(" and "),
    thenText: (rule) => (rule.actions || []).map(actionText).filter(Boolean).join(" · "),
    runsText: (rule) => (rule.runCount > 0 ? `${rule.runCount.toLocaleString("en-US")} violations` : "-"),
    lastRunText: (rule) => (rule.lastRunAt > 0 ? func.prettifyEpoch(rule.lastRunAt) : "Never"),
    emptyForm: () => ({ ruleId: "", name: "", active: true, conditions: [{ field: "severity", values: [] }], actions: [] }),
    formFromRule: (rule, duplicate = false) => ({
        ruleId: duplicate ? "" : rule.hexId,
        name: duplicate ? `${rule.name} (copy)` : rule.name,
        active: duplicate ? false : rule.active,
        conditions: (rule.conditions || []).map((condition) => ({ field: condition.field, values: [...(condition.values || [])] })),
        actions: (rule.actions || []).map((action) => ({ type: action.type, config: { ...(action.config || {}) } })),
    }),
    payloadFromForm: (form) => ({
        ruleId: form.ruleId || undefined,
        name: form.name.trim(),
        active: form.active,
        conditions: form.conditions.map((condition) => ({
            field: condition.field,
            operator: condition.values.length > 1 ? "in" : "is",
            values: condition.values,
        })),
        actions: form.actions,
    }),
    validateForm: (form) => {
        if (!form.name.trim()) return "Give the rule a name";
        if (form.name.trim().length > RULE_NAME_MAX) return `The name can be at most ${RULE_NAME_MAX} characters`;
        if (form.conditions.length === 0 || form.conditions.some((condition) => condition.values.length === 0)) return "Every condition needs a value";
        if (form.conditions.some((condition) => condition.field === "unassignedHours" && !(Number(condition.values[0]) > 0))) return "Unassigned hours must be a positive number";
        if (form.actions.length === 0) return "Add at least one action";
        const assign = form.actions.find((action) => action.type === "ASSIGN");
        if (assign && !assign.config.assignee.trim()) return "Say who the violation is assigned to";
        const alert = form.actions.find((action) => action.type === "ALERT");
        if (alert && alert.config.channels.length === 0) return "Pick at least one alert channel";
        if (alert && alert.config.channels.includes("email") && !alert.config.emails.trim()) return "Enter an email address for the alert";
        return null;
    },
};

export default transform;
