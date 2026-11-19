import { VerticalStack, HorizontalStack, Text, Box, RadioButton, Badge } from "@shopify/polaris";
import { useEffect, useMemo, useReducer } from "react";
import { ConditionRows, CountPopover, conditionsReducer, usePruneStaleConditionValues } from "./ConditionRows";
import { deviceIdSuffix } from "./useAgenticUserOptions";

// Enforcement matches the tag / device rows together, or a picked user (see filterPoliciesByDevice in guardrails).
const USER_OR_TYPES = ['USER'];

// "Device Tags & Users" card (guardrail Scope step and Audit Data blocks): everyone, or tags / devices / users.
const DeviceUserTargetingCard = ({
    title = "Device Tags & Users",
    applyToAllUsers,
    setApplyToAllUsers,
    targetTags,
    setTargetTags,
    targetDeviceIds,
    setTargetDeviceIds,
    targetUserNames,
    setTargetUserNames,
    negatedTargetTags,
    setNegatedTargetTags,
    negatedTargetDeviceIds,
    setNegatedTargetDeviceIds,
    negatedTargetUserNames,
    setNegatedTargetUserNames,
    availableTagKeyValues = [],
    availableDevices,
    availableUsers = [],
    matchingDeviceRows = [],
    usersLoading,
    deviceList = [],
    showUserConditionError = false,
}) => {
    const [userConditions, userDispatch] = useReducer(conditionsReducer, null, () => {
        // A negated DEVICE/USER row with zero values ("Exclude nothing") is meaningful — keep it.
        const conds = Object.entries(targetTags || {})
            .filter(([, values]) => (values || []).length > 0)
            .map(([key, values]) => ({ type: key, values, otherValues: [], negated: !!negatedTargetTags?.[key] }));
        if ((targetDeviceIds || []).length > 0 || negatedTargetDeviceIds) conds.push({ type: 'DEVICE', values: targetDeviceIds, otherValues: [], negated: negatedTargetDeviceIds });
        if ((targetUserNames || []).length > 0 || negatedTargetUserNames) conds.push({ type: 'USER', values: targetUserNames, otherValues: [], negated: negatedTargetUserNames });
        return conds;
    });

    useEffect(() => {
        const tagMap = {};
        userConditions.filter(c => c.type !== 'DEVICE' && c.type !== 'USER').forEach(c => {
            const values = (c.values || []).filter(Boolean);
            if (values.length) tagMap[c.type] = values;
        });
        setTargetTags(tagMap);
        // Devices and Users are two fully independent dropdown pools (see
        // availableDevices/availableUsers) AND two independent backend fields — a Users pick
        // must never land in targetDeviceIds, which is device-id-only and drives
        // applyToDeviceIds resolution server-side.
        setTargetDeviceIds(userConditions.filter(c => c.type === 'DEVICE').flatMap(c => c.values).filter(Boolean));
        setTargetUserNames(userConditions.filter(c => c.type === 'USER').flatMap(c => c.values).filter(Boolean));
    }, [userConditions]);

    const deviceOptionsSorted = useMemo(
        () => (availableDevices || []).slice().sort((a, b) => a.label.localeCompare(b.label)),
        [availableDevices]
    );
    const userOptionsSorted = useMemo(
        () => (availableUsers || []).slice().sort((a, b) => a.label.localeCompare(b.label)),
        [availableUsers]
    );
    const tagOptionsSortedByKey = useMemo(() => {
        const map = {};
        (availableTagKeyValues || []).forEach(({ key, values }) => {
            map[key] = (values || []).map(v => ({ label: v, value: v })).sort((a, b) => a.label.localeCompare(b.label));
        });
        return map;
    }, [availableTagKeyValues]);

    const getOptionsForType = (type) => {
        switch (type) {
            case 'DEVICE': return deviceOptionsSorted;
            case 'USER': return userOptionsSorted;
            default: return tagOptionsSortedByKey[type] || [];
        }
    };

    // Device Tags & Users: DEVICE and USER (two independent pools) plus any dynamic tag key (team, role, department, ...).
    const deviceValueSet = new Set((availableDevices || []).map(o => o.value));
    const userValueSet = new Set((availableUsers || []).map(o => o.value));
    usePruneStaleConditionValues(
        true,
        deviceValueSet.size + userValueSet.size + (availableTagKeyValues || []).reduce((acc, k) => acc + (k.values || []).length, 0) > 0,
        userConditions, userDispatch,
        {
            DEVICE: deviceValueSet,
            USER: userValueSet,
            ...Object.fromEntries((availableTagKeyValues || []).map(({ key, values }) => [key, new Set(values || [])])),
        }
    );

    const deviceCount = (availableDevices || []).length;
    const userCount = (availableUsers || []).length;
    const tagTypeOptions = (availableTagKeyValues || []).map(({ key, values }) => {
        const label = key.charAt(0).toUpperCase() + key.slice(1);
        const count = (values || []).length;
        return { label: `${count > 1 ? `${label}s` : label} [${count}]`, value: key, disabled: count === 0 };
    });
    // Users and Devices are kept as two distinct, unambiguous options rather than one merged
    // "Users" pool — selecting one makes explicit whether the row targets a person (by identity,
    // beta) or a physical device (by device id, the mature/GA path).
    const userTypeOptions = [
        ...tagTypeOptions,
        { label: `${userCount > 1 ? 'Users' : 'User'} [${userCount}] (Beta)`, value: 'USER', disabled: userCount === 0 },
        { label: `${deviceCount > 1 ? 'Devices' : 'Device'} [${deviceCount}]`, value: 'DEVICE', disabled: deviceCount === 0 },
    ];

    // TAG keys are dynamic, so negation is per key rather than fixed booleans like the agentic
    // buckets above; DEVICE and USER each only ever have one row, so they stay plain booleans.
    const userNegationProps = (type) => {
        switch (type) {
            case 'DEVICE': return { negated: negatedTargetDeviceIds, onToggle: setNegatedTargetDeviceIds };
            case 'USER': return { negated: negatedTargetUserNames, onToggle: setNegatedTargetUserNames };
            default: return {
                negated: !!negatedTargetTags?.[type],
                onToggle: (val) => setNegatedTargetTags(prev => ({ ...(prev || {}), [type]: val })),
            };
        }
    };

    return (
            <Box borderColor="border" borderWidth="1" borderRadius="2" background="bg-surface">
                <Box padding="4">
                    <VerticalStack gap="4">
                        <Text variant="headingSm">{title}</Text>
                        <VerticalStack gap="2">
                            <RadioButton
                                label={
                                    <HorizontalStack gap="2" blockAlign="center">
                                        <Text variant="bodyMd">Apply to all</Text>
                                        <Badge tone="success">Recommended</Badge>
                                    </HorizontalStack>
                                }
                                checked={applyToAllUsers}
                                id="apply_to_all_users"
                                name="userTargeting"
                                onChange={() => setApplyToAllUsers(true)}
                                helpText={deviceList.length > 0
                                    ? (
                                        <HorizontalStack gap="1" blockAlign="center" wrap>
                                            <Text variant="bodyMd" tone="subdued">Applies to all</Text>
                                            <CountPopover count={deviceList.length} label="Users" items={deviceList} />
                                            <Text variant="bodyMd" tone="subdued">in your organization.</Text>
                                        </HorizontalStack>
                                    )
                                    : "Applies to all users in your organization."
                                }
                            />
                            <RadioButton
                                label="Select Device Tags & Users"
                                checked={!applyToAllUsers}
                                id="select_users_teams"
                                name="userTargeting"
                                onChange={() => setApplyToAllUsers(false)}
                                disabled={!usersLoading && (availableTagKeyValues || []).length === 0 && (availableDevices || []).length === 0 && (availableUsers || []).length === 0}
                                helpText={(() => {
                                    if (usersLoading) {
                                        return "Loading device tags and users…";
                                    }
                                    const noOptions = (availableTagKeyValues || []).length === 0 && (availableDevices || []).length === 0 && (availableUsers || []).length === 0;
                                    if (noOptions) {
                                        return "No device tags or users found. Devices must report in before you can target them here.";
                                    }
                                    if (userConditions.length === 0) {
                                        return "Choose specific device tags or Users.";
                                    }
                                    // Devices and Users are two independent pools (see targetDeviceIds/targetUserNames) —
                                    // shown as two separate counts rather than one merged "N users" figure, which would
                                    // hide which half of the selection actually contributed the count.
                                    // Negated (Exclude) means "everyone except these picks" — mirror matchingDeviceRows'
                                    // negation handling instead of showing the raw pick count/list, which would render
                                    // an Exclude-3 selection as if only 3 people were in scope instead of the other ~97.
                                    const matchedUserItems = negatedTargetUserNames
                                        ? (availableUsers || []).filter(o => !(targetUserNames || []).includes(o.value))
                                        : (targetUserNames || []).map(name => {
                                            const option = (availableUsers || []).find(o => o.value === name);
                                            return { value: name, label: option?.label || name };
                                        });
                                    const nonZeroItems = [
                                        matchingDeviceRows.length > 0 && { count: matchingDeviceRows.length, label: "Devices", items: matchingDeviceRows.map(r => ({ value: r.deviceId, label: `${r.username} · ${deviceIdSuffix(r.deviceId)}` })) },
                                        matchedUserItems.length > 0 && { count: matchedUserItems.length, label: "Users", items: matchedUserItems },
                                    ].filter(Boolean);
                                    if (nonZeroItems.length === 0) {
                                        return "Applies to 0 devices or users with the current selection.";
                                    }
                                    return (
                                        <HorizontalStack gap="1" blockAlign="center" wrap>
                                            <Text variant="bodyMd" tone="subdued">Applies to</Text>
                                            {nonZeroItems.flatMap((item, i) => [
                                                <CountPopover key={item.label} count={item.count} label={item.label} items={item.items} />,
                                                i < nonZeroItems.length - 1 && (
                                                    <Text key={`sep-${i}`} variant="bodyMd" tone="subdued">and</Text>
                                                )
                                            ]).filter(Boolean)}
                                            <Text variant="bodyMd" tone="subdued">with the current selection.</Text>
                                        </HorizontalStack>
                                    );
                                })()}
                            />
                            {!applyToAllUsers && (
                                <Box paddingInlineStart="6">
                                    <ConditionRows conditions={userConditions} typeOptions={userTypeOptions} dispatch={userDispatch} operator="AND" showError={showUserConditionError} getNegationProps={userNegationProps} getOptionsForType={getOptionsForType} orTypes={USER_OR_TYPES} />
                                </Box>
                            )}
                        </VerticalStack>
                    </VerticalStack>
                </Box>
            </Box>
    );
};

export default DeviceUserTargetingCard;
