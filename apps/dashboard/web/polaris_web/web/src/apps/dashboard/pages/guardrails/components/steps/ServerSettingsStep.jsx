import { VerticalStack, HorizontalStack, Text, FormLayout, Box, Checkbox, RadioButton, Divider, Popover, TextField, Link, Tag } from "@shopify/polaris";
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
                <Box padding="2" minWidth="240px">
                    <TextField
                        placeholder={`Search ${label}...`}
                        value={search}
                        onChange={setSearch}
                        autoComplete="off"
                    />
                </Box>
            </Popover.Pane>
            <Popover.Pane>
                <Box padding="3">
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

    const compatibleMcpServers = isBlockMode ? (mcpServers || []).filter(s => s.isInline) : (mcpServers || []);
    const compatibleAgentServers = isBlockMode ? (agentServers || []).filter(s => s.isInline) : (agentServers || []);
    const compatibleBrowserLlmServers = isBlockMode ? (browserLlmServers || []).filter(s => s.isInline) : (browserLlmServers || []);

    const hasIncompatibleServers = !isAtlas && isBlockMode && (
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

                            {/* Agentic Assets section */}
                            <VerticalStack gap="3">
                                <Text variant="headingSm">Agentic Assets</Text>
                                <VerticalStack gap="2">
                                    <RadioButton
                                        label="Apply to all"
                                        checked={applyToAllServers === true}
                                        id="apply_to_all_servers"
                                        name="serverTargeting"
                                        onChange={() => setApplyToAllServers(true)}
                                        helpText={
                                            <HorizontalStack gap="1" blockAlign="center" wrap>
                                                <Text variant="bodyMd" tone="subdued">This currently includes</Text>
                                                <CountPopover count={agentCountItems.length} label="Agents" items={agentCountItems} />
                                                <Text variant="bodyMd" tone="subdued">,</Text>
                                                <CountPopover count={mcpCountItems.length} label="MCP Servers" items={mcpCountItems} />
                                                <Text variant="bodyMd" tone="subdued">&amp;</Text>
                                                <CountPopover count={llmCountItems.length} label="LLMs" items={llmCountItems} />
                                            </HorizontalStack>
                                        }
                                    />

                                    <RadioButton
                                        label="Select Agentic Assets"
                                        checked={applyToAllServers === false}
                                        id="edit_servers"
                                        name="serverTargeting"
                                        onChange={() => setApplyToAllServers(false)}
                                        helpText={hasIncompatibleServers
                                            ? "Some Agentic Assets are disabled, block mode requires servers running in inline (sync) mode."
                                            : "Choose specific agents, MCP servers, or LLMs to apply this guardrail to."}
                                    />

                                    {applyToAllServers === false && (
                                        <Box paddingInlineStart="6">
                                            <FormLayout>
                                                <FormLayout.Group>
                                                    <DropdownSearch
                                                        label="Select Agents"
                                                        placeholder="Choose agents"
                                                        optionsList={agentOptions}
                                                        setSelected={setSelectedAgentServers}
                                                        preSelected={selectedAgentServers}
                                                        allowMultiple={true}
                                                        showSelectAllMinOptions={isBlockMode && !isAtlas ? 99999 : 1}
                                                        disabled={collectionsLoading}
                                                        sliceMaxVal={Math.max(selectedAgentServers.length + 20, 20)}
                                                        value={selectedAgentServers.length > 0 ? `${selectedAgentServers.length} Selected` : undefined}
                                                    />
                                                    <DropdownSearch
                                                        label="Select MCP Servers"
                                                        placeholder="Choose MCP servers"
                                                        optionsList={mcpOptions}
                                                        setSelected={setSelectedMcpServers}
                                                        preSelected={selectedMcpServers}
                                                        allowMultiple={true}
                                                        showSelectAllMinOptions={isBlockMode && !isAtlas ? 99999 : 1}
                                                        disabled={collectionsLoading}
                                                        sliceMaxVal={Math.max(selectedMcpServers.length + 20, 20)}
                                                        value={selectedMcpServers.length > 0 ? `${selectedMcpServers.length} Selected` : undefined}
                                                    />
                                                </FormLayout.Group>
                                                <DropdownSearch
                                                    label="Select LLMs"
                                                    placeholder="Choose LLMs"
                                                    optionsList={llmOptions}
                                                    setSelected={setSelectedBrowserLlms}
                                                    preSelected={selectedBrowserLlms}
                                                    allowMultiple={true}
                                                    showSelectAllMinOptions={isBlockMode && !isAtlas ? 99999 : 1}
                                                    disabled={collectionsLoading}
                                                    sliceMaxVal={Math.max(selectedBrowserLlms.length + 20, 20)}
                                                    value={selectedBrowserLlms.length > 0 ? `${selectedBrowserLlms.length} Selected` : undefined}
                                                />
                                            </FormLayout>
                                        </Box>
                                    )}
                                </VerticalStack>
                            </VerticalStack>

                            {isAtlas && <Divider />}

                            {/* Users & Roles section — Atlas only */}
                            {isAtlas &&
                            <VerticalStack gap="3">
                                <Text variant="headingSm">Users & Roles</Text>
                                <VerticalStack gap="2">
                                    <RadioButton
                                        label="Apply to all"
                                        checked={applyToAllUsers === true}
                                        id="apply_to_all_users"
                                        name="userTargeting"
                                        onChange={() => setApplyToAllUsers(true)}
                                        helpText={
                                            <HorizontalStack gap="1" blockAlign="center" wrap>
                                                <Text variant="bodyMd" tone="subdued">This currently includes</Text>
                                                <CountPopover count={(availableTeams || []).length} label="Teams" items={(availableTeams || []).map(t => ({ label: t, value: t }))} />
                                                <Text variant="bodyMd" tone="subdued">&amp;</Text>
                                                <CountPopover count={(availableRoles || []).length} label="Roles" items={(availableRoles || []).map(r => ({ label: r, value: r }))} />
                                            </HorizontalStack>
                                        }
                                    />

                                    <RadioButton
                                        label="Select Users & Teams"
                                        checked={applyToAllUsers === false}
                                        id="select_users_teams"
                                        name="userTargeting"
                                        onChange={() => setApplyToAllUsers(false)}
                                        helpText="Choose specific teams or roles to limit this guardrail to their members."
                                    />

                                    {applyToAllUsers === false && (
                                        <Box paddingInlineStart="6">
                                            <FormLayout>
                                                <FormLayout.Group>
                                                    <DropdownSearch
                                                        label="Teams"
                                                        placeholder="Choose teams"
                                                        optionsList={(availableTeams || []).map(t => ({ label: t, value: t }))}
                                                        setSelected={setTargetTeams}
                                                        preSelected={targetTeams}
                                                        allowMultiple={true}
                                                        disabled={usersLoading}
                                                        value={targetTeams?.length > 0 ? `${targetTeams.length} Selected` : undefined}
                                                    />
                                                    <DropdownSearch
                                                        label="Roles"
                                                        placeholder="Choose roles"
                                                        optionsList={(availableRoles || []).map(r => ({ label: r, value: r }))}
                                                        setSelected={setTargetRoles}
                                                        preSelected={targetRoles}
                                                        allowMultiple={true}
                                                        disabled={usersLoading}
                                                        value={targetRoles?.length > 0 ? `${targetRoles.length} Selected` : undefined}
                                                    />
                                                </FormLayout.Group>
                                            </FormLayout>
                                        </Box>
                                    )}
                                </VerticalStack>
                            </VerticalStack>}

                        </VerticalStack>
                    </Box>

                    {/* Scope footer */}
                    <Box background="bg-magic-subdued" borderColor="border-subdued" padding="3" borderRadiusEndStart="2" borderRadiusEndEnd="2">
                        <Text as="span" variant="bodySm" color="magic">
                            This scope applies to {scopeAssetCount} agentic asset{scopeAssetCount !== 1 ? 's' : ''}{isAtlas && !applyToAllUsers && (targetTeams?.length > 0 || targetRoles?.length > 0) ? ` for selected users` : ''}.
                        </Text>
                    </Box>
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
