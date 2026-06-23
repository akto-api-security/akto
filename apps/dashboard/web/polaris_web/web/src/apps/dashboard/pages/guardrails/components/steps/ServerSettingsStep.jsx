import { VerticalStack, HorizontalStack, Text, FormLayout, Box, Checkbox, RadioButton, Icon, Popover, TextField, Link, Tag, Banner, Badge } from "@shopify/polaris";
import { InfoMinor } from "@shopify/polaris-icons";
import { useState, useEffect } from "react";
import DropdownSearch from "../../../../components/shared/DropdownSearch";
import OwaspTag from "../OwaspTag";
import RuleEnforcementDropdown from "../RuleEnforcementDropdown";
import { isEndpointSecurityCategory } from "../../../../../main/labelHelper";
import { extractEndpointId } from "../../../observe/agentic/constants";

// Strip deviceId prefix using the same extractEndpointId as the Agentic Assets screen
// 'macbook.cursor.time' → 'cursor.time', merging duplicates across devices
const stripDeviceId = (label) => {
    const deviceId = extractEndpointId(label);
    return deviceId ? label.slice(deviceId.length + 1) : label;
};

const groupByService = (servers) => {
    const seen = {};
    (servers || []).forEach(s => {
        const key = stripDeviceId(s.label) || s.label;
        if (!seen[key]) seen[key] = { label: key, value: key };
    });
    return Object.values(seen);
};

export const ServerSettingsConfig = {
    number: 10,
    title: "Scope",

    validate: () => {
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
    const filtered = (items || []).filter(item =>
        (item.label || '').toLowerCase().includes(search.toLowerCase())
    );
    return (
        <Popover
            active={active}
            activator={
                <Link onClick={() => setActive(v => !v)}>
                    {count} {label}
                </Link>
            }
            onClose={() => { setActive(false); setSearch(''); }}
        >
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
}) => {
    const isBlockMode = policyBehaviour === 'block';
    const isAtlas = isEndpointSecurityCategory();

    const isApplyToAll = isAtlas ? (applyToAllServers && applyToAllUsers) : applyToAllServers;

    const handleScopeChange = (applyAll) => {
        setApplyToAllServers(applyAll);
        if (isAtlas) setApplyToAllUsers(applyAll);
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

    // Atlas: group by service suffix (strips deviceId, merges duplicates)
    // Argus: raw per-device list with disabled state for block mode
    const mcpOptions = isAtlas
        ? sortSelectedFirst(groupByService(mcpServers), selectedMcpServers)
        : sortSelectedFirst(
            isBlockMode ? (mcpServers || []).map(s => ({ ...s, disabled: !s.isInline })) : (mcpServers || []),
            selectedMcpServers
          );
    const agentOptions = isAtlas
        ? sortSelectedFirst(groupByService(agentServers), selectedAgentServers)
        : sortSelectedFirst(
            isBlockMode ? (agentServers || []).map(s => ({ ...s, disabled: !s.isInline })) : (agentServers || []),
            selectedAgentServers
          );
    const llmOptions = isAtlas
        ? sortSelectedFirst(groupByService(browserLlmServers), selectedBrowserLlms)
        : sortSelectedFirst(
            isBlockMode ? (browserLlmServers || []).map(s => ({ ...s, disabled: !s.isInline })) : (browserLlmServers || []),
            selectedBrowserLlms
          );

    // Count items for "Apply to all" helpText
    const mcpCountItems = isAtlas ? groupByService(compatibleMcpServers) : compatibleMcpServers;
    const agentCountItems = isAtlas ? groupByService(compatibleAgentServers) : compatibleAgentServers;
    const llmCountItems = isAtlas ? groupByService(compatibleBrowserLlmServers) : compatibleBrowserLlmServers;

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

    const scopeAssetCount = applyToAllServers
        ? (mcpCountItems.length + agentCountItems.length + llmCountItems.length)
        : (selectedMcpServers.length + selectedAgentServers.length + selectedBrowserLlms.length);

    return (
        <VerticalStack gap="4">
            <Text variant="bodyMd" tone="subdued">
                Configure which servers the guardrail should be applied to and specify whether it applies to requests, responses, or both.
            </Text>
            <OwaspTag stepNumber={10} />

            <FormLayout>
                {/* Scope card */}
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
                                    checked={isApplyToAll}
                                    id="apply_to_all"
                                    name="scopeTargeting"
                                    onChange={() => handleScopeChange(true)}
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
                                    checked={!isApplyToAll}
                                    id="advance_configuration"
                                    name="scopeTargeting"
                                    onChange={() => handleScopeChange(false)}
                                    helpText={isAtlas
                                        ? "Choose specific Agents, MCP Servers, LLMs, Roles and Teams."
                                        : "Choose specific Agents, MCP Servers, or LLMs to apply this guardrail to."}
                                />

                                {!isApplyToAll && (
                                    <Box paddingInlineStart="6">
                                        <FormLayout>
                                            <FormLayout.Group>
                                                <DropdownSearch
                                                    id="scope-agents"
                                                    label="Select Agents"
                                                    placeholder={agentOptions.length === 0 ? "Not available" : "Choose agents"}
                                                    optionsList={agentOptions}
                                                    setSelected={setSelectedAgentServers}
                                                    preSelected={selectedAgentServers}
                                                    allowMultiple={true}
                                                    showSelectAllMinOptions={isBlockMode && !isAtlas ? 99999 : 1}
                                                    disabled={collectionsLoading || agentOptions.length === 0}
                                                    sliceMaxVal={Math.max(selectedAgentServers.length + 20, 20)}
                                                    value={selectedAgentServers.length > 0 ? `${selectedAgentServers.length} Selected` : undefined}
                                                />
                                                <DropdownSearch
                                                    id="scope-mcp-servers"
                                                    label="Select MCP Servers"
                                                    placeholder={mcpOptions.length === 0 ? "Not available" : "Choose MCP servers"}
                                                    optionsList={mcpOptions}
                                                    setSelected={setSelectedMcpServers}
                                                    preSelected={selectedMcpServers}
                                                    allowMultiple={true}
                                                    showSelectAllMinOptions={isBlockMode && !isAtlas ? 99999 : 1}
                                                    disabled={collectionsLoading || mcpOptions.length === 0}
                                                    sliceMaxVal={Math.max(selectedMcpServers.length + 20, 20)}
                                                    value={selectedMcpServers.length > 0 ? `${selectedMcpServers.length} Selected` : undefined}
                                                />
                                            </FormLayout.Group>
                                            <FormLayout.Group>
                                                <DropdownSearch
                                                    id="scope-llms"
                                                    label="Select LLMs"
                                                    placeholder={llmOptions.length === 0 ? "Not available" : "Choose LLMs"}
                                                    optionsList={llmOptions}
                                                    setSelected={setSelectedBrowserLlms}
                                                    preSelected={selectedBrowserLlms}
                                                    allowMultiple={true}
                                                    showSelectAllMinOptions={isBlockMode && !isAtlas ? 99999 : 1}
                                                    disabled={collectionsLoading || llmOptions.length === 0}
                                                    sliceMaxVal={Math.max(selectedBrowserLlms.length + 20, 20)}
                                                    value={selectedBrowserLlms.length > 0 ? `${selectedBrowserLlms.length} Selected` : undefined}
                                                />
                                                {isAtlas && (
                                                    <DropdownSearch
                                                        id="scope-roles"
                                                        label="Roles"
                                                        placeholder={(availableRoles || []).length === 0 ? "Not available" : "Choose roles"}
                                                        optionsList={(availableRoles || []).map(r => ({ label: r, value: r }))}
                                                        setSelected={setTargetRoles}
                                                        preSelected={targetRoles}
                                                        allowMultiple={true}
                                                        disabled={usersLoading || (availableRoles || []).length === 0}
                                                        value={targetRoles?.length > 0 ? `${targetRoles.length} Selected` : undefined}
                                                    />
                                                )}
                                            </FormLayout.Group>
                                            {isAtlas && (
                                                <FormLayout.Group>
                                                    <DropdownSearch
                                                        id="scope-teams"
                                                        label="Teams"
                                                        placeholder={(availableTeams || []).length === 0 ? "Not available" : "Choose teams"}
                                                        optionsList={(availableTeams || []).map(t => ({ label: t, value: t }))}
                                                        setSelected={setTargetTeams}
                                                        preSelected={targetTeams}
                                                        allowMultiple={true}
                                                        disabled={usersLoading || (availableTeams || []).length === 0}
                                                        value={targetTeams?.length > 0 ? `${targetTeams.length} Selected` : undefined}
                                                    />
                                                    <Box />
                                                </FormLayout.Group>
                                            )}
                                            <Banner tone="info">
                                                Some agentic assets are disabled. Block mode requires servers running in inline (sync) mode.
                                            </Banner>
                                        </FormLayout>
                                    </Box>
                                )}
                            </VerticalStack>
                        </VerticalStack>
                    </Box>

                    {/* Scope footer — commented out for now
                    <Box background="bg-magic-subdued" borderColor="border-subdued" padding="3" borderRadiusEndStart="2" borderRadiusEndEnd="2">
                        <Text as="span" variant="bodySm" color="magic">
                            This scope applies to {scopeAssetCount} agentic asset{scopeAssetCount !== 1 ? 's' : ''}{isAtlas && !isApplyToAll && (targetTeams?.length > 0 || targetRoles?.length > 0) ? ` for selected users` : ''}.
                        </Text>
                    </Box>
                    */}
                </Box>

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
