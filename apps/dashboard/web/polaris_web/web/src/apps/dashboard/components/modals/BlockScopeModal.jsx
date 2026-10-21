import { useState } from "react";
import { Modal, VerticalStack, HorizontalStack, Text } from "@shopify/polaris";
import DeviceUserTargetingCard from "../../pages/guardrails/components/targeting/DeviceUserTargetingCard";
import { useAgenticUserOptions, orgUuidFromUserId } from "../../pages/guardrails/components/targeting/useAgenticUserOptions";
import { describeUserScope, scopePart } from "../../pages/guardrails/components/targeting/scopeSummary";
import { CountPopover } from "../../pages/guardrails/components/targeting/ConditionRows";
import { ALL_VALUES_SENTINEL } from "../shared/DropdownSearch";

// Reads a block's scope back; users as org userId, else userName, like the picker.
export const blockScopeOf = (row) => ({
    targetTags: row?.targetTags || {},
    negatedTargetTags: row?.negatedTargetTags || {},
    targetDeviceIds: row?.targetDeviceIds || [],
    negatedTargetDeviceIds: !!row?.negatedTargetDeviceIds,
    targetUserNames: (row?.userMetadata || []).map(u => (orgUuidFromUserId(u.userId) ? u.userId : u.userName)).filter(Boolean),
    negatedTargetUserNames: !!row?.negatedTargetUserNames,
});

// "For 1 Team & 3 Users" with a hover list per part; renders nothing when the block applies to everyone.
export const BlockScopeSummary = ({ row }) => {
    const scope = blockScopeOf(row);
    const plural = (label, count) => `${label}${count !== 1 ? 's' : ''}`;
    const items = (values) => (values || []).filter(v => v !== ALL_VALUES_SENTINEL).map(v => ({ value: v, label: v }));
    const userItems = (row?.userMetadata || []).map(u => {
        const name = u.userName || u.userEmail;
        return { value: u.userId || name, label: u.userEmail && u.userEmail !== name ? `${u.userEmail} · ${name}` : name };
    });
    const segments = [
        ...Object.entries(scope.targetTags).map(([key, values]) => {
            const label = plural(key.charAt(0).toUpperCase() + key.slice(1), values.length);
            return { text: scopePart(!!scope.negatedTargetTags[key], values, label), label, items: items(values) };
        }),
        { text: scopePart(scope.negatedTargetDeviceIds, scope.targetDeviceIds, plural('Device', scope.targetDeviceIds.length)), label: 'Devices', items: items(scope.targetDeviceIds) },
        { text: scopePart(scope.negatedTargetUserNames, scope.targetUserNames, plural('User', scope.targetUserNames.length)), label: 'Users', items: userItems, isOr: true },
    ].filter(seg => seg.text);
    if (segments.length === 0) return null;
    // Same reading as the picker: tag / device parts together, or the picked users.
    const andCount = segments.filter(seg => !seg.isOr).length;
    const separator = (i) => segments[i + 1].isOr ? 'or' : (i === andCount - 2 ? '&' : ',');
    return (
        // Clicking a list link shouldn't also select the table row.
        <div onClick={(e) => e.stopPropagation()}>
            <HorizontalStack gap="1" blockAlign="center" wrap>
                <Text variant="bodySm" color="subdued">For</Text>
                {segments.flatMap((seg, i) => [
                    <CountPopover key={seg.label} text={seg.text} label={seg.label} items={seg.items} />,
                    i < segments.length - 1 && (
                        <Text key={`sep-${i}`} variant="bodySm" color="subdued">{separator(i)}</Text>
                    ),
                ]).filter(Boolean)}
            </HorizontalStack>
        </div>
    );
};

// Audit Data block dialog: everyone, or chosen tags / devices / users; onBlock gets the scope ({} = everyone).
const BlockScopeModal = ({ title, description, initialScope, onClose, onBlock }) => {
    const initial = initialScope || blockScopeOf(null);
    const [applyToAllUsers, setApplyToAllUsers] = useState(!describeUserScope(initial));
    const [targetTags, setTargetTags] = useState(initial.targetTags);
    const [negatedTargetTags, setNegatedTargetTags] = useState(initial.negatedTargetTags);
    const [targetDeviceIds, setTargetDeviceIds] = useState(initial.targetDeviceIds);
    const [negatedTargetDeviceIds, setNegatedTargetDeviceIds] = useState(initial.negatedTargetDeviceIds);
    const [targetUserNames, setTargetUserNames] = useState(initial.targetUserNames);
    const [negatedTargetUserNames, setNegatedTargetUserNames] = useState(initial.negatedTargetUserNames);
    const [showError, setShowError] = useState(false);
    const [saving, setSaving] = useState(false);

    const options = useAgenticUserOptions({ applyToAllUsers, targetTags, negatedTargetTags, targetDeviceIds, negatedTargetDeviceIds });
    const scope = { targetTags, negatedTargetTags, targetDeviceIds, negatedTargetDeviceIds, targetUserNames, negatedTargetUserNames };
    const nothingSelected = !applyToAllUsers && !describeUserScope(scope);

    const handleBlock = async () => {
        if (nothingSelected) {
            setShowError(true);
            return;
        }
        setSaving(true);
        try {
            await onBlock(applyToAllUsers ? {} : {
                ...scope,
                // Only keep Include / Exclude flags for tag keys that still have values.
                negatedTargetTags: Object.fromEntries(Object.entries(negatedTargetTags || {}).filter(([key]) => (targetTags?.[key] || []).length > 0)),
            });
        } finally {
            setSaving(false);
        }
    };

    return (
        <Modal
            open
            onClose={onClose}
            title={title}
            primaryAction={{
                content: applyToAllUsers ? "Block for everyone" : "Block selected users",
                destructive: true,
                loading: saving,
                onAction: handleBlock,
            }}
            secondaryActions={[{ content: "Cancel", onAction: onClose }]}
        >
            <Modal.Section>
                <VerticalStack gap="4">
                    <Text variant="bodyMd" color="subdued">{description}</Text>
                    <DeviceUserTargetingCard
                        title="Who is blocked"
                        applyToAllUsers={applyToAllUsers}
                        setApplyToAllUsers={setApplyToAllUsers}
                        targetTags={targetTags}
                        setTargetTags={setTargetTags}
                        targetDeviceIds={targetDeviceIds}
                        setTargetDeviceIds={setTargetDeviceIds}
                        targetUserNames={targetUserNames}
                        setTargetUserNames={setTargetUserNames}
                        negatedTargetTags={negatedTargetTags}
                        setNegatedTargetTags={setNegatedTargetTags}
                        negatedTargetDeviceIds={negatedTargetDeviceIds}
                        setNegatedTargetDeviceIds={setNegatedTargetDeviceIds}
                        negatedTargetUserNames={negatedTargetUserNames}
                        setNegatedTargetUserNames={setNegatedTargetUserNames}
                        availableTagKeyValues={options.availableTagKeyValues}
                        availableDevices={options.availableDevices}
                        availableUsers={options.availableUsers}
                        matchingDeviceRows={options.matchingDeviceRows}
                        usersLoading={options.usersLoading}
                        deviceList={options.availableUsers}
                        showUserConditionError={showError && nothingSelected}
                    />
                </VerticalStack>
            </Modal.Section>
        </Modal>
    );
};

export default BlockScopeModal;
