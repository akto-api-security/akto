import { useEffect, useMemo, useState } from "react";
import {
    ActionList, Banner, Box, Button, Card, Checkbox, ChoiceList, Divider, HorizontalStack, Link, Modal, Popover, Text, TextField, VerticalStack,
} from "@shopify/polaris";
import { DeleteMinor } from "@shopify/polaris-icons";
import func from "@/util/func";
import DropdownSearch from "../../../components/shared/DropdownSearch";
import OperatorDropdown from "../../../components/layouts/OperatorDropdown";
import api from "../api";
import {
    ACTION_TYPES, ALERT_CHANNELS, CONDITION_FIELDS, CONDITION_KIND, DESTINATION_PAGES, DUE_OPTIONS, IGNORE_EXPIRY_OPTIONS, IGNORE_REASONS,
    PRIORITY_OPTIONS, RULE_NAME_MAX, TICKET_DESTINATIONS,
} from "./constants";
import transform, { defaultAction } from "./transform";

function DropdownField({ label, options, value, onChange }) {
    const current = options.find((option) => option.value === value)?.label || "Select";
    return (
        <VerticalStack gap="1">
            {label && <Text as="span" variant="bodyMd">{label}</Text>}
            <Box width="max-content">
                <OperatorDropdown label={current} items={options} selected={onChange} designer />
            </Box>
        </VerticalStack>
    );
}

function ConditionValue({ definition, values, policyOptions, onChange }) {
    if (definition.kind === CONDITION_KIND.MULTI) {
        return <ChoiceList allowMultiple title={definition.label} titleHidden choices={definition.options} selected={values} onChange={onChange} />;
    }
    if (definition.kind === CONDITION_KIND.POLICY) {
        return (
            <DropdownSearch
                placeholder="Search policies"
                optionsList={policyOptions}
                allowMultiple
                preSelected={values}
                setSelected={onChange}
            />
        );
    }
    if (definition.kind === CONDITION_KIND.HOURS) {
        return <TextField label={definition.label} labelHidden type="number" min={1} autoComplete="off" value={values[0] || ""} onChange={(value) => onChange(value ? [value] : [])} />;
    }
    return <TextField label={definition.label} labelHidden autoComplete="off" placeholder="For example claude or cursor" value={values[0] || ""} onChange={(value) => onChange(value.trim() ? [value] : [])} />;
}

function ConditionRow({ condition, usedFields, policyOptions, onChange, onRemove, removable }) {
    const definition = CONDITION_FIELDS.find((entry) => entry.field === condition.field);
    const fieldOptions = CONDITION_FIELDS
        .filter((entry) => entry.field === condition.field || !usedFields.includes(entry.field))
        .map((entry) => ({ label: entry.label, value: entry.field }));
    return (
        <Card padding="3">
            <VerticalStack gap="3">
                <HorizontalStack align="space-between" blockAlign="end" wrap={false} gap="2">
                    <DropdownField options={fieldOptions} value={condition.field} onChange={(field) => onChange({ field, values: [] })} />
                    {removable && <Button icon={DeleteMinor} plain accessibilityLabel="Remove condition" onClick={onRemove} />}
                </HorizontalStack>
                <ConditionValue definition={definition} values={condition.values} policyOptions={policyOptions} onChange={(values) => onChange({ ...condition, values })} />
            </VerticalStack>
        </Card>
    );
}

function destinationConnected(destinations, id) {
    return !!destinations.find((entry) => entry.id === id)?.connected;
}

function NotConnected({ destinationId, destinations }) {
    if (destinationConnected(destinations, destinationId)) return null;
    return (
        <Banner status="warning">
            <Text as="span">This destination is not connected yet. </Text>
            <Link url={DESTINATION_PAGES[destinationId]} target="_blank">Connect it in integrations</Link>
        </Banner>
    );
}

function ActionFields({ action, destinations, onChange }) {
    const config = action.config;
    const set = (patch) => onChange({ ...action, config: { ...config, ...patch } });
    switch (action.type) {
        case "CREATE_TICKET":
            return (
                <VerticalStack gap="3">
                    <HorizontalStack gap="3" wrap={false}>
                        <Box width="50%"><DropdownField label="Destination" options={TICKET_DESTINATIONS} value={config.destination} onChange={(destination) => set({ destination })} /></Box>
                        <Box width="50%"><DropdownField label="Priority" options={PRIORITY_OPTIONS} value={config.priority} onChange={(priority) => set({ priority })} /></Box>
                    </HorizontalStack>
                    <NotConnected destinationId={config.destination} destinations={destinations} />
                    <Checkbox label="Include redacted evidence" helpText="Raw payloads are never sent to the ticket" checked={!!config.includeEvidence} onChange={(includeEvidence) => set({ includeEvidence })} />
                    <Checkbox label="Sync status back" helpText="Resolve the violation when the ticket closes" checked={!!config.syncStatus} onChange={(syncStatus) => set({ syncStatus })} />
                </VerticalStack>
            );
        case "ASSIGN":
            return (
                <VerticalStack gap="3">
                    <TextField label="Assign to" autoComplete="off" placeholder="Name or email" value={config.assignee} onChange={(assignee) => set({ assignee })} />
                    <DropdownField label="Due" options={DUE_OPTIONS} value={config.dueHours} onChange={(dueHours) => set({ dueHours })} />
                    <Checkbox label="Notify the assignee in Slack" checked={!!config.notifySlack} onChange={(notifySlack) => set({ notifySlack })} />
                </VerticalStack>
            );
        case "ALERT":
            return (
                <VerticalStack gap="3">
                    <ChoiceList
                        allowMultiple
                        title="Send to"
                        choices={ALERT_CHANNELS.map((channel) => ({
                            label: channel.destination && !destinationConnected(destinations, channel.destination) ? `${channel.label} (not connected)` : channel.label,
                            value: channel.value,
                        }))}
                        selected={config.channels}
                        onChange={(channels) => set({ channels })}
                    />
                    {config.channels.includes("slack") && (
                        <TextField label="Slack channel" autoComplete="off" placeholder="#atlas-alerts" value={config.slackChannel} onChange={(slackChannel) => set({ slackChannel })} />
                    )}
                    {config.channels.includes("email") && (
                        <TextField label="Email addresses" autoComplete="off" placeholder="security-oncall@example.com" helpText="Separate several with commas" value={config.emails} onChange={(emails) => set({ emails })} />
                    )}
                    {config.channels.includes("slack") && <NotConnected destinationId="SLACK" destinations={destinations} />}
                    {config.channels.includes("webhook") && <NotConnected destinationId="WEBHOOK" destinations={destinations} />}
                </VerticalStack>
            );
        case "IGNORE":
            return (
                <VerticalStack gap="3">
                    <ChoiceList title="Reason" choices={IGNORE_REASONS} selected={[config.reason]} onChange={(values) => set({ reason: values[0] })} />
                    <DropdownField label="Expires" options={IGNORE_EXPIRY_OPTIONS} value={config.expiryDays} onChange={(expiryDays) => set({ expiryDays })} />
                </VerticalStack>
            );
        case "COACH_EMPLOYEE":
            return (
                <TextField
                    label="Message"
                    multiline={3}
                    autoComplete="off"
                    placeholder="Leave empty to send the default message that explains the policy and the approved alternative"
                    value={config.message}
                    onChange={(message) => set({ message })}
                />
            );
        case "NOTIFY_MANAGER":
            return (
                <HorizontalStack gap="3" wrap={false}>
                    <Box width="50%"><TextField label="After this many events" type="number" min={1} autoComplete="off" value={config.afterEvents} onChange={(afterEvents) => set({ afterEvents })} /></Box>
                    <Box width="50%"><TextField label="Within days" type="number" min={1} autoComplete="off" value={config.windowDays} onChange={(windowDays) => set({ windowDays })} /></Box>
                </HorizontalStack>
            );
        default:
            return null;
    }
}

function ActionCard({ action, destinations, onChange, onRemove }) {
    const definition = ACTION_TYPES.find((entry) => entry.type === action.type);
    return (
        <Card padding="3">
            <VerticalStack gap="3">
                <HorizontalStack align="space-between" blockAlign="center">
                    <VerticalStack gap="05">
                        <Text as="h4" variant="headingSm">{definition.label}</Text>
                        <Text as="span" variant="bodySm" color="subdued">{definition.help}</Text>
                    </VerticalStack>
                    <Button icon={DeleteMinor} plain accessibilityLabel={`Remove ${definition.label}`} onClick={onRemove} />
                </HorizontalStack>
                <Divider />
                <ActionFields action={action} destinations={destinations} onChange={onChange} />
            </VerticalStack>
        </Card>
    );
}

function AddActionButton({ actions, onAdd }) {
    const [open, setOpen] = useState(false);
    const remaining = ACTION_TYPES.filter((definition) => !actions.some((action) => action.type === definition.type));
    if (remaining.length === 0) return null;
    return (
        <Popover
            active={open}
            onClose={() => setOpen(false)}
            activator={<Button disclosure onClick={() => setOpen((current) => !current)}>Add action</Button>}
        >
            <ActionList
                items={remaining.map((definition) => ({
                    content: definition.label,
                    helpText: definition.help,
                    onAction: () => {
                        onAdd(defaultAction(definition.type));
                        setOpen(false);
                    },
                }))}
            />
        </Popover>
    );
}

function ResponseRuleModal({ open, initialForm, destinations, onClose, onSaved }) {
    const [form, setForm] = useState(initialForm);
    const [policyOptions, setPolicyOptions] = useState([]);
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState("");

    useEffect(() => {
        if (!open) return;
        setForm(initialForm);
        setError("");
    }, [open, initialForm]);

    useEffect(() => {
        if (!open || policyOptions.length > 0) return undefined;
        let ignore = false;
        api.fetchAllGuardrailPolicies()
            .then((policies) => {
                if (ignore) return;
                const names = [...new Set(policies.map((policy) => policy.name).filter(Boolean))];
                setPolicyOptions(names.map((name) => ({ label: name, value: name })));
            })
            .catch(() => {
                if (!ignore) func.setToast(true, true, "Could not load guardrail policies");
            });
        return () => { ignore = true; };
    }, [open, policyOptions.length]);

    const usedFields = useMemo(() => form.conditions.map((condition) => condition.field), [form.conditions]);
    const canAddCondition = form.conditions.length < CONDITION_FIELDS.length;
    const isEdit = !!form.ruleId;

    const patchCondition = (index, next) => setForm((current) => ({ ...current, conditions: current.conditions.map((condition, i) => (i === index ? next : condition)) }));
    const removeCondition = (index) => setForm((current) => ({ ...current, conditions: current.conditions.filter((_, i) => i !== index) }));
    const addCondition = () => {
        const field = CONDITION_FIELDS.find((entry) => !usedFields.includes(entry.field));
        setForm((current) => ({ ...current, conditions: [...current.conditions, { field: field.field, values: [] }] }));
    };
    const patchAction = (index, next) => setForm((current) => ({ ...current, actions: current.actions.map((action, i) => (i === index ? next : action)) }));
    const removeAction = (index) => setForm((current) => ({ ...current, actions: current.actions.filter((_, i) => i !== index) }));
    const addAction = (action) => setForm((current) => ({ ...current, actions: [...current.actions, action] }));

    const save = async () => {
        const problem = transform.validateForm(form);
        if (problem) {
            setError(problem);
            return;
        }
        setSaving(true);
        setError("");
        try {
            await api.saveGuardrailResponseRule(transform.payloadFromForm(form));
            func.setToast(true, false, isEdit ? "Response rule updated" : "Response rule created");
            onSaved();
        } catch (e) {
            setError("Could not save the rule. Check the details and try again.");
        } finally {
            setSaving(false);
        }
    };

    return (
        <Modal
            large
            open={open}
            onClose={onClose}
            title={isEdit ? "Edit response rule" : "Create response rule"}
            primaryAction={{ content: isEdit ? "Save rule" : "Create rule", onAction: save, loading: saving }}
            secondaryActions={[{ content: "Cancel", onAction: onClose }]}
        >
            <Modal.Section>
                <VerticalStack gap="4">
                    {error && <Banner status="critical" onDismiss={() => setError("")}>{error}</Banner>}
                    <TextField
                        label="Rule name"
                        autoComplete="off"
                        maxLength={RULE_NAME_MAX}
                        showCharacterCount
                        placeholder="For example: Critical secrets, ticket and page"
                        value={form.name}
                        onChange={(name) => setForm((current) => ({ ...current, name }))}
                    />
                    <VerticalStack gap="2">
                        <Text as="h3" variant="headingSm">When a violation matches all of these</Text>
                        {form.conditions.map((condition, index) => (
                            <ConditionRow
                                key={condition.field}
                                condition={condition}
                                usedFields={usedFields}
                                policyOptions={policyOptions}
                                removable={form.conditions.length > 1}
                                onChange={(next) => patchCondition(index, next)}
                                onRemove={() => removeCondition(index)}
                            />
                        ))}
                        {canAddCondition && <Box><Button onClick={addCondition}>Add condition</Button></Box>}
                    </VerticalStack>
                    <VerticalStack gap="2">
                        <Text as="h3" variant="headingSm">Then do</Text>
                        {form.actions.length === 0 && <Text as="p" color="subdued">No actions yet. Add at least one.</Text>}
                        {form.actions.map((action, index) => (
                            <ActionCard
                                key={action.type}
                                action={action}
                                destinations={destinations}
                                onChange={(next) => patchAction(index, next)}
                                onRemove={() => removeAction(index)}
                            />
                        ))}
                        <Box><AddActionButton actions={form.actions} onAdd={addAction} /></Box>
                    </VerticalStack>
                    <Checkbox
                        label="Turn this rule on"
                        helpText="Rules take effect on new violations once Atlas starts running them"
                        checked={form.active}
                        onChange={(active) => setForm((current) => ({ ...current, active }))}
                    />
                </VerticalStack>
            </Modal.Section>
        </Modal>
    );
}

export default ResponseRuleModal;
