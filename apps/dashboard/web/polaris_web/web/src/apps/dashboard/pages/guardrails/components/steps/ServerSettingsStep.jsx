import { VerticalStack, HorizontalStack, Text, FormLayout, Box, Checkbox, RadioButton, Popover, TextField, Link, Tag, Banner, Badge, Button, InlineError } from "@shopify/polaris";
import { DeleteMinor } from "@shopify/polaris-icons";
import { useState, useEffect, useRef } from "react";
import DropdownSearch from "../../../../components/shared/DropdownSearch";
import Dropdown from "../../../../components/layouts/Dropdown";
import AssetIcon from "../../../observe/agentic/AssetIcon";
import { formatDisplayName } from "../../../observe/agentic/mcpClientHelper";
import OwaspTag from "../OwaspTag";
import RuleEnforcementDropdown from "../RuleEnforcementDropdown";
import { isEndpointSecurityCategory } from "../../../../../main/labelHelper";


export const ServerSettingsConfig = {
    number: 10,
    title: "Scope",

    validate: ({ serverScopeLeftDirty, userScopeLeftDirty }) => {
        if (serverScopeLeftDirty || userScopeLeftDirty) {
            return { isValid: false, errorMessage: "Select at least one condition or switch to Apply to all." };
        }
        return { isValid: true, errorMessage: null };
    },

    getSummary: ({ applyToAllServers, applyToAllUsers, selectedMcpServers, selectedAgentServers, selectedBrowserLlms, mcpServers, agentServers, browserLlmServers, applyOnRequest, applyOnResponse, policyBehaviour, targetTeams, targetRoles }) => {
        const appSettings = (applyOnRequest || applyOnResponse) ?
            ` - ${applyOnRequest ? 'Req' : ''}${applyOnRequest && applyOnResponse ? '/' : ''}${applyOnResponse ? 'Res' : ''}` : '';
        const behaviourSuffix = policyBehaviour ? `Rule behaviour: ${policyBehaviour}` : '';
        let summary = '';
        if (applyToAllServers) {
            summary += 'All assets';
        } else {
            const parts = [];
            if (selectedMcpServers?.length > 0) parts.push(`${selectedMcpServers.length} MCP`);
            if (selectedAgentServers?.length > 0) parts.push(`${selectedAgentServers.length} Agents`);
            if (selectedBrowserLlms?.length > 0) parts.push(`${selectedBrowserLlms.length} LLMs`);
            summary += parts.join(', ') || 'No assets';
        }
        if (applyToAllUsers) {
            summary += ' | All users';
        } else {
            if (targetTeams?.length > 0) summary += ` | Teams: ${targetTeams.slice(0, 2).join(', ')}${targetTeams.length > 2 ? ` +${targetTeams.length - 2}` : ''}`;
            if (targetRoles?.length > 0) summary += ` Roles: ${targetRoles.slice(0, 2).join(', ')}${targetRoles.length > 2 ? ` +${targetRoles.length - 2}` : ''}`;
        }
        summary += `${appSettings} ${behaviourSuffix}`;
        return summary;
    }
};

const CountPopover = ({ count, label, items }) => {
    const [active, setActive] = useState(false);
    const [search, setSearch] = useState('');
    const closeTimer = useRef(null);

    const open = () => { if (closeTimer.current) clearTimeout(closeTimer.current); setActive(true); };
    const scheduleClose = () => { closeTimer.current = setTimeout(() => { setActive(false); setSearch(''); }, 150); };

    const filtered = (items || []).filter(item =>
        (item.label || '').toLowerCase().includes(search.toLowerCase())
    );
    return (
        <Popover
            active={active}
            activator={
                <span onMouseEnter={open} onMouseLeave={scheduleClose} style={{ cursor: 'pointer' }}>
                    <Link>{count} {label}</Link>
                </span>
            }
            onClose={() => { setActive(false); setSearch(''); }}
        >
            <div onMouseEnter={open} onMouseLeave={scheduleClose}>
                <Popover.Pane fixed>
                    <Box padding="2" minWidth="240px" maxWidth="320px">
                        <TextField
                            placeholder={`Search ${label}...`}
                            value={search}
                            onChange={setSearch}
                            autoComplete="off"
                        />
                    </Box>
                </Popover.Pane>
                <Popover.Pane>
                    <Box padding="3" maxWidth="320px">
                        {filtered.length === 0
                            ? <Text tone="subdued" variant="bodySm">No {label} found</Text>
                            : <HorizontalStack gap="2" wrap>
                                {filtered.map(item => (
                                    <Tag key={item.value}>{item.label}</Tag>
                                ))}
                              </HorizontalStack>
                        }
                    </Box>
                </Popover.Pane>
            </div>
        </Popover>
    );
};

const ServerSettingsStep = ({
    applyToAllServers,
    setApplyToAllServers,
    selectedMcpServers,
    setSelectedMcpServers,
    selectedAgentServers,
    setSelectedAgentServers,
    selectedBrowserLlms,
    setSelectedBrowserLlms,
    applyOnResponse,
    setApplyOnResponse,
    applyOnRequest,
    setApplyOnRequest,
    mcpServers,
    agentServers,
    browserLlmServers,
    collectionsLoading,
    policyBehaviour,
    setPolicyBehaviour,
    targetTeams,
    setTargetTeams,
    targetRoles,
    setTargetRoles,
    availableTeams,
    availableRoles,
    usersLoading,
    applyToAllUsers,
    setApplyToAllUsers,
    showConditionError = false,
    showUserConditionError = false,
}) => {
    const isBlockMode = policyBehaviour === 'block';
    const isAtlas = isEndpointSecurityCategory();

    const [agenticConditions, setAgenticConditions] = useState(() => {
        const conds = [];
        if ((selectedAgentServers || []).length > 0) conds.push({ type: 'AGENT', values: selectedAgentServers });
        if ((selectedMcpServers || []).length > 0) conds.push({ type: 'MCP_SERVER', values: selectedMcpServers });
        if ((selectedBrowserLlms || []).length > 0) conds.push({ type: 'LLM', values: selectedBrowserLlms });
        return conds;
    });

    const [userConditions, setUserConditions] = useState(() => {
        const conds = [];
        if ((targetRoles || []).length > 0) conds.push({ type: 'ROLE', values: targetRoles });
        if ((targetTeams || []).length > 0) conds.push({ type: 'TEAM', values: targetTeams });
        return conds;
    });

    useEffect(() => {
        setSelectedAgentServers(agenticConditions.filter(c => c.type === 'AGENT').flatMap(c => c.values).filter(Boolean));
        setSelectedMcpServers(agenticConditions.filter(c => c.type === 'MCP_SERVER').flatMap(c => c.values).filter(Boolean));
        setSelectedBrowserLlms(agenticConditions.filter(c => c.type === 'LLM').flatMap(c => c.values).filter(Boolean));
    }, [agenticConditions]);

    useEffect(() => {
        if (isAtlas) {
            setTargetRoles(userConditions.filter(c => c.type === 'ROLE').flatMap(c => c.values).filter(Boolean));
            setTargetTeams(userConditions.filter(c => c.type === 'TEAM').flatMap(c => c.values).filter(Boolean));
        }
    }, [userConditions]);

    const addAgenticCondition = (type) => setAgenticConditions(prev => [...prev, { type: type || 'AGENT', values: [] }]);
    const deleteAgenticCondition = (index) => setAgenticConditions(prev => prev.filter((_, i) => i !== index));
    const clearAgenticConditions = () => setAgenticConditions([]);
    const updateAgenticCondition = (index, updates) => setAgenticConditions(prev => prev.map((c, i) => i === index ? { ...c, ...updates } : c));

    const addUserCondition = (type) => setUserConditions(prev => [...prev, { type: type || 'TEAM', values: [] }]);
    const deleteUserCondition = (index) => setUserConditions(prev => prev.filter((_, i) => i !== index));
    const clearUserConditions = () => setUserConditions([]);
    const updateUserCondition = (index, updates) => setUserConditions(prev => prev.map((c, i) => i === index ? { ...c, ...updates } : c));

    const TYPE_PREFIXES = ['ai-agent.', 'mcp-server.', 'browser-llm.', 'skill.'];
    const stripTypePrefix = (label) => {
        for (const p of TYPE_PREFIXES) if (label.startsWith(p)) return label.slice(p.length);
        return label;
    };

    const enrichOptions = (options, assetType) =>
        options.map(opt => {
            const svc = stripTypePrefix(opt.label);
            return { ...opt, label: formatDisplayName(svc), media: <AssetIcon type={assetType} assetTagValue={svc} size={16} /> };
        }).sort((a, b) => a.label.localeCompare(b.label));

    const getOptionsForType = (type) => {
        switch (type) {
            case 'AGENT': return enrichOptions(agentOptions, 'AI Agent');
            case 'MCP_SERVER': return enrichOptions(mcpOptions, 'MCP Server');
            case 'LLM': return enrichOptions(llmOptions, 'LLM');
            case 'ROLE': return (availableRoles || []).map(r => ({ label: r, value: r })).sort((a, b) => a.label.localeCompare(b.label));
            case 'TEAM': return (availableTeams || []).map(t => ({ label: t, value: t })).sort((a, b) => a.label.localeCompare(b.label));
            default: return [];
        }
    };

    const compatibleMcpServers = isBlockMode ? (mcpServers || []).filter(s => s.isInline) : (mcpServers || []);
    const compatibleAgentServers = isBlockMode ? (agentServers || []).filter(s => s.isInline) : (agentServers || []);
    const compatibleBrowserLlmServers = isBlockMode ? (browserLlmServers || []).filter(s => s.isInline) : (browserLlmServers || []);

    const hasIncompatibleServers = isBlockMode && (
        (mcpServers || []).some(s => !s.isInline) ||
        (agentServers || []).some(s => !s.isInline) ||
        (browserLlmServers || []).some(s => !s.isInline)
    );

    const sortSelectedFirst = (options, selected) => {
        const selectedSet = new Set(selected);
        return [
            ...options.filter(o => selectedSet.has(o.value)),
            ...options.filter(o => !selectedSet.has(o.value)),
        ];
    };

    // Atlas: filterCollections() already groups by service/platform key — use directly.
    // Argus: raw per-device list with disabled state for block mode.
    const mcpOptions = isAtlas
        ? sortSelectedFirst(mcpServers || [], selectedMcpServers)
        : sortSelectedFirst(
            isBlockMode ? (mcpServers || []).map(s => ({ ...s, disabled: !s.isInline })) : (mcpServers || []),
            selectedMcpServers
          );
    const agentOptions = isAtlas
        ? sortSelectedFirst(agentServers || [], selectedAgentServers)
        : sortSelectedFirst(
            isBlockMode ? (agentServers || []).map(s => ({ ...s, disabled: !s.isInline })) : (agentServers || []),
            selectedAgentServers
          );
    const llmOptions = isAtlas
        ? sortSelectedFirst(browserLlmServers || [], selectedBrowserLlms)
        : sortSelectedFirst(
            isBlockMode ? (browserLlmServers || []).map(s => ({ ...s, disabled: !s.isInline })) : (browserLlmServers || []),
            selectedBrowserLlms
          );

    // One-time Atlas cleanup: strips stale condition values that don't match a current option.
    // Skipped for Argus — backward-compat values may not yet have a matching option.
    const normalizedRef = useRef(false);
    useEffect(() => {
        if (!isAtlas) return;
        if (normalizedRef.current) return;
        const total = agentOptions.length + mcpOptions.length + llmOptions.length;
        if (total === 0) return;
        normalizedRef.current = true;
        const agentVals = new Set(agentOptions.map(o => o.value));
        const mcpVals = new Set(mcpOptions.map(o => o.value));
        const llmVals = new Set(llmOptions.map(o => o.value));
        setAgenticConditions(prev => {
            let changed = false;
            const next = prev.map(c => {
                const valSet = c.type === 'AGENT' ? agentVals : c.type === 'MCP_SERVER' ? mcpVals : c.type === 'LLM' ? llmVals : null;
                if (!valSet || valSet.size === 0) return c;
                const valid = (c.values || []).filter(v => valSet.has(v));
                if (valid.length === (c.values || []).length) return c;
                changed = true;
                return { ...c, values: valid };
            });
            return changed ? next : prev;
        });
    }, [agentOptions.length, mcpOptions.length, llmOptions.length]);

    const agenticTypeOptions = [
        { label: `${agentOptions.length > 1 ? 'AI Agents' : 'AI Agent'} [${agentOptions.length}]`, value: 'AGENT', disabled: agentOptions.length === 0 },
        { label: `${mcpOptions.length > 1 ? 'MCP Servers' : 'MCP Server'} [${mcpOptions.length}]`, value: 'MCP_SERVER', disabled: mcpOptions.length === 0 },
        { label: `LLM${llmOptions.length > 1 ? 's' : ''} [${llmOptions.length}]`, value: 'LLM', disabled: llmOptions.length === 0 },
    ];

    const teamCount = (availableTeams || []).length;
    const roleCount = (availableRoles || []).length;
    const userTypeOptions = [
        { label: `${teamCount > 1 ? 'Teams' : 'Team'} [${teamCount}]`, value: 'TEAM', disabled: teamCount === 0 },
        { label: `${roleCount > 1 ? 'Roles' : 'Role'} [${roleCount}]`, value: 'ROLE', disabled: roleCount === 0 },
    ];

    const renderConditionRows = (conditions, typeOptions, onUpdate, onDelete, onAdd, onClear, operator = 'OR', showError = false) => {
        const usedTypes = new Set(conditions.map(c => c.type));
        const availableTypeOptions = typeOptions.filter(o => !o.disabled);
        const allTypesFilled = availableTypeOptions.length === 0 || availableTypeOptions.every(o => usedTypes.has(o.value));
        const nextUnusedType = availableTypeOptions.find(o => !usedTypes.has(o.value))?.value;

        return (
            <VerticalStack gap="3">
                {conditions.map((condition, index) => {
                    const otherUsedTypes = new Set(conditions.filter((_, i) => i !== index).map(c => c.type));
                    const rowTypeOptions = typeOptions.map(o => ({
                        ...o,
                        disabled: o.disabled || otherUsedTypes.has(o.value),
                    }));
                    return (
                        <HorizontalStack key={index} gap="2" blockAlign="center">
                            <div style={{ minWidth: 64, textAlign: 'right' }}>
                                {index === 0
                                    ? <Text variant="bodyMd" tone="subdued" fontWeight="medium">Where my</Text>
                                    : <Text variant="bodyMd" tone="subdued" fontWeight="medium">{operator.charAt(0).toUpperCase() + operator.slice(1).toLowerCase()}</Text>
                                }
                            </div>
                            <div style={{ flex: '1', minWidth: 0 }}>
                                <Dropdown
                                    id={`cond-type-${condition.type}-${index}`}
                                    menuItems={rowTypeOptions}
                                    disabledOptions={rowTypeOptions.filter(o => o.disabled).map(o => o.value)}
                                    initial={rowTypeOptions.find(o => o.value === condition.type)?.label || rowTypeOptions[0]?.label}
                                    selected={(val) => onUpdate(index, { type: val, values: [] })}
                                />
                            </div>
                            <Text variant="bodyMd" tone="subdued" fontWeight="medium">{getOptionsForType(condition.type).length > 1 ? 'are' : 'is'}</Text>
                            <div style={{ flex: '2', minWidth: 0 }}>
                                <DropdownSearch
                                    id={`cond-val-${condition.type}-${index}`}
                                    placeholder="Select value"
                                    optionsList={sortSelectedFirst(getOptionsForType(condition.type), condition.values || [])}
                                    setSelected={(vals) => onUpdate(index, { values: vals })}
                                    preSelected={condition.values || []}
                                    allowMultiple={true}
                                    disabled={getOptionsForType(condition.type).length === 0}
                                    value={(condition.values || []).length > 0 ? `${condition.values.length} selected` : undefined}
                                    sliceMaxVal={getOptionsForType(condition.type).length || 20}
                                />
                            </div>
                            <Button icon={DeleteMinor} onClick={() => onDelete(index)} />
                        </HorizontalStack>
                    );
                })}
                <HorizontalStack gap="4" blockAlign="center">
                    {!allTypesFilled && <Button onClick={() => onAdd(nextUnusedType)}>Add condition</Button>}
                    {conditions.length > 0 && (
                        <Button plain destructive onClick={onClear}>Clear all</Button>
                    )}
                </HorizontalStack>
                {showError && conditions.length === 0 && (
                    <InlineError message='Add at least one condition, or switch to "Apply to all".' fieldID="" />
                )}
            </VerticalStack>
        );
    };

    // Count items for "Apply to all" helpText — use same grouping as the dropdowns, with enriched display names.
    // Atlas: filterCollections() already produces one entry per service/platform key.
    // Argus: use compatible servers (filtered by inline for block mode).
    const mcpCountItems = enrichOptions(isAtlas ? (mcpServers || []) : compatibleMcpServers, 'MCP Server');
    const agentCountItems = enrichOptions(isAtlas ? (agentServers || []) : compatibleAgentServers, 'AI Agent');
    const llmCountItems = enrichOptions(isAtlas ? (browserLlmServers || []) : compatibleBrowserLlmServers, 'LLM');

    useEffect(() => {
        // Atlas uses app-level groups — per-device inline filtering doesn't apply
        if (!isBlockMode || isAtlas) return;
        if (selectedMcpServers?.length > 0) {
            const compatible = selectedMcpServers.filter(val => (mcpServers || []).find(s => s.value === val && s.isInline));
            if (compatible.length !== selectedMcpServers.length) setSelectedMcpServers(compatible);
        }
        if (selectedAgentServers?.length > 0) {
            const compatible = selectedAgentServers.filter(val => (agentServers || []).find(s => s.value === val && s.isInline));
            if (compatible.length !== selectedAgentServers.length) setSelectedAgentServers(compatible);
        }
        if (selectedBrowserLlms?.length > 0) {
            const compatible = selectedBrowserLlms.filter(val => (browserLlmServers || []).find(s => s.value === val && s.isInline));
            if (compatible.length !== selectedBrowserLlms.length) setSelectedBrowserLlms(compatible);
        }
    }, [policyBehaviour]);

    return (
        <VerticalStack gap="4">
            <Text variant="bodyMd" tone="subdued">
                Configure which servers the guardrail should be applied to and specify whether it applies to requests, responses, or both.
            </Text>
            <OwaspTag stepNumber={10} />

            <FormLayout>
                {isAtlas && (
                    <Box borderColor="border" borderWidth="1" borderRadius="2" background="bg-surface">
                        <Box padding="4">
                            <VerticalStack gap="4">
                                <Text variant="headingSm">Agentic Assets</Text>
                                <VerticalStack gap="2">
                                    <RadioButton
                                        label={
                                            <HorizontalStack gap="2" blockAlign="center">
                                                <Text variant="bodyMd">Apply to all</Text>
                                                <Badge tone="success">Recommended</Badge>
                                            </HorizontalStack>
                                        }
                                        checked={applyToAllServers}
                                        id="apply_to_all_assets"
                                        name="agenticTargeting"
                                        onChange={() => setApplyToAllServers(true)}
                                        helpText={(() => {
                                            const nonZeroItems = [
                                                agentCountItems.length > 0 && { count: agentCountItems.length, label: "Agents", items: agentCountItems },
                                                mcpCountItems.length > 0 && { count: mcpCountItems.length, label: "MCP Servers", items: mcpCountItems },
                                                llmCountItems.length > 0 && { count: llmCountItems.length, label: "LLMs", items: llmCountItems },
                                            ].filter(Boolean);
                                            return (
                                                <HorizontalStack gap="1" blockAlign="center" wrap>
                                                    <Text variant="bodyMd" tone="subdued">This includes</Text>
                                                    {nonZeroItems.flatMap((item, i) => [
                                                        <CountPopover key={item.label} count={item.count} label={item.label} items={item.items} />,
                                                        i < nonZeroItems.length - 1 && (
                                                            <Text key={`sep-${i}`} variant="bodyMd" tone="subdued">
                                                                {i === nonZeroItems.length - 2 ? '&' : ','}
                                                            </Text>
                                                        )
                                                    ]).filter(Boolean)}
                                                    <Text variant="bodyMd" tone="subdued">.</Text>
                                                </HorizontalStack>
                                            );
                                        })()}
                                    />
                                    <RadioButton
                                        label="Select Agentic Assets"
                                        checked={!applyToAllServers}
                                        id="select_agentic_assets"
                                        name="agenticTargeting"
                                        onChange={() => setApplyToAllServers(false)}
                                        helpText="Choose specific Agents, MCP Servers & LLMs."
                                    />
                                    {!applyToAllServers && (
                                        <Box paddingInlineStart="6">
                                            {renderConditionRows(agenticConditions, agenticTypeOptions, updateAgenticCondition, deleteAgenticCondition, addAgenticCondition, clearAgenticConditions, 'OR', showConditionError)}
                                        </Box>
                                    )}
                                </VerticalStack>
                            </VerticalStack>
                        </Box>
                    </Box>
                )}
                {isAtlas && (
                    <Box borderColor="border" borderWidth="1" borderRadius="2" background="bg-surface">
                        <Box padding="4">
                            <VerticalStack gap="4">
                                <Text variant="headingSm">Teams & Roles</Text>
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
                                        helpText={(() => {
                                            const parts = [
                                                (availableTeams || []).length > 0 && { count: (availableTeams || []).length, label: "Teams", items: (availableTeams || []).map(t => ({ label: t, value: t })) },
                                                (availableRoles || []).length > 0 && { count: (availableRoles || []).length, label: "Roles", items: (availableRoles || []).map(r => ({ label: r, value: r })) },
                                            ].filter(Boolean);
                                            if (parts.length === 0) return "Applies to all users in your organization.";
                                            return (
                                                <HorizontalStack gap="1" blockAlign="center" wrap>
                                                    <Text variant="bodyMd" tone="subdued">This includes</Text>
                                                    {parts.flatMap((item, i) => [
                                                        <CountPopover key={item.label} count={item.count} label={item.label} items={item.items} />,
                                                        i < parts.length - 1 && <Text key={`sep-${i}`} variant="bodyMd" tone="subdued">&</Text>
                                                    ]).filter(Boolean)}
                                                    <Text variant="bodyMd" tone="subdued">.</Text>
                                                </HorizontalStack>
                                            );
                                        })()}
                                    />
                                    <RadioButton
                                        label="Select Teams & Roles"
                                        checked={!applyToAllUsers}
                                        id="select_users_teams"
                                        name="userTargeting"
                                        onChange={() => setApplyToAllUsers(false)}
                                        disabled={(availableTeams || []).length === 0 && (availableRoles || []).length === 0}
                                        helpText={(availableTeams || []).length === 0 && (availableRoles || []).length === 0
                                            ? "No Teams or Roles configured. Set them up in your organization settings before selecting."
                                            : "Choose specific Teams & Roles."
                                        }
                                    />
                                    {!applyToAllUsers && (
                                        <Box paddingInlineStart="6">
                                            {renderConditionRows(userConditions, userTypeOptions, updateUserCondition, deleteUserCondition, addUserCondition, clearUserConditions, 'AND', showUserConditionError)}
                                        </Box>
                                    )}
                                </VerticalStack>
                            </VerticalStack>
                        </Box>
                    </Box>
                )}
                {!isAtlas && (
                    <Box borderColor="border" borderWidth="1" borderRadius="2" background="bg-surface">
                        <Box padding="4">
                            <VerticalStack gap="4">
                                <Text variant="headingSm">Coverage</Text>
                                <VerticalStack gap="2">
                                    <RadioButton
                                        label={
                                            <HorizontalStack gap="2" blockAlign="center">
                                                <Text variant="bodyMd">Apply to all</Text>
                                                <Badge tone="success">Recommended</Badge>
                                            </HorizontalStack>
                                        }
                                        checked={applyToAllServers}
                                        id="apply_to_all"
                                        name="scopeTargeting"
                                        onChange={() => setApplyToAllServers(true)}
                                        helpText={(() => {
                                            const nonZeroItems = [
                                                agentCountItems.length > 0 && { count: agentCountItems.length, label: "Agents", items: agentCountItems },
                                                mcpCountItems.length > 0 && { count: mcpCountItems.length, label: "MCP Servers", items: mcpCountItems },
                                                llmCountItems.length > 0 && { count: llmCountItems.length, label: "LLMs", items: llmCountItems },
                                            ].filter(Boolean);
                                            return (
                                                <HorizontalStack gap="1" blockAlign="center" wrap>
                                                    <Text variant="bodyMd" tone="subdued">This includes</Text>
                                                    {nonZeroItems.flatMap((item, i) => [
                                                        <CountPopover key={item.label} count={item.count} label={item.label} items={item.items} />,
                                                        i < nonZeroItems.length - 1 && (
                                                            <Text key={`sep-${i}`} variant="bodyMd" tone="subdued">
                                                                {i === nonZeroItems.length - 2 ? '&' : ','}
                                                            </Text>
                                                        )
                                                    ]).filter(Boolean)}
                                                    <Text variant="bodyMd" tone="subdued">.</Text>
                                                </HorizontalStack>
                                            );
                                        })()}
                                    />
                                    <RadioButton
                                        label="Advance Configuration"
                                        checked={!applyToAllServers}
                                        id="advance_configuration"
                                        name="scopeTargeting"
                                        onChange={() => setApplyToAllServers(false)}
                                        helpText="Choose specific Agents, MCP Servers, or LLMs to apply this guardrail to."
                                    />
                                    {!applyToAllServers && (
                                        <Box paddingInlineStart="6">
                                            {renderConditionRows(agenticConditions, agenticTypeOptions, updateAgenticCondition, deleteAgenticCondition, addAgenticCondition, clearAgenticConditions, 'OR', showConditionError)}
                                            {hasIncompatibleServers && (
                                                <Banner tone="info">
                                                    Some agentic assets are disabled. Block mode requires servers running in inline (sync) mode.
                                                </Banner>
                                            )}
                                        </Box>
                                    )}
                                </VerticalStack>
                            </VerticalStack>
                        </Box>
                    </Box>
                )}

                {/* Rule enforcement */}
                <Box padding="4" borderColor="border" borderWidth="1" borderRadius="2" background="bg-surface">
                    <VerticalStack gap="3">
                        <RuleEnforcementDropdown
                            id="policy-rule-behaviour"
                            value={policyBehaviour}
                            onChange={setPolicyBehaviour}
                        />
                    </VerticalStack>
                </Box>

                {/* Application settings */}
                <Box padding="4" borderColor="border" borderWidth="1" borderRadius="2" background="bg-surface">
                    <VerticalStack gap="3">
                        <Text variant="headingSm">Application Settings</Text>
                        <Text variant="bodyMd" tone="subdued">
                            Specify whether the guardrail should be applied to responses and/or requests.
                        </Text>

                        <VerticalStack gap="2">
                            <Checkbox
                                label="Apply guardrail to responses"
                                checked={applyOnResponse}
                                onChange={setApplyOnResponse}
                                helpText="When enabled, this guardrail will filter and evaluate model responses before they're sent to users."
                            />

                            <Checkbox
                                label="Apply guardrail to requests"
                                checked={applyOnRequest}
                                onChange={setApplyOnRequest}
                                helpText="When enabled, this guardrail will filter and evaluate user inputs before they're processed by the model."
                            />
                        </VerticalStack>
                    </VerticalStack>
                </Box>
            </FormLayout>
        </VerticalStack>
    );
};

export default ServerSettingsStep;
