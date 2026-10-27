import { VerticalStack, HorizontalStack, Text, FormLayout, Box, Checkbox, RadioButton, Banner, Badge } from "@shopify/polaris";
import { useEffect, useReducer } from "react";
import { ALL_VALUES_SENTINEL } from "../../../../components/shared/DropdownSearch";
import AssetIcon from "../../../observe/agentic/AssetIcon";
import { formatDisplayName } from "../../../observe/agentic/mcpClientHelper";
import OwaspTag from "../OwaspTag";
import RuleEnforcementDropdown from "../RuleEnforcementDropdown";
import { isEndpointSecurityCategory } from "../../../../../main/labelHelper";
import { ConditionRows, CountPopover, conditionsReducer, usePruneStaleConditionValues, sortSelectedFirst } from "../targeting/ConditionRows";
import DeviceUserTargetingCard from "../targeting/DeviceUserTargetingCard";
import { scopePart, describeUserScope } from "../targeting/scopeSummary";

export const ServerSettingsConfig = {
    number: 10,
    title: "Scope",

    validate: ({ serverScopeLeftDirty, userScopeLeftDirty }) => {
        if (serverScopeLeftDirty || userScopeLeftDirty) {
            return { isValid: false, errorMessage: "Select at least one condition or switch to Apply to all." };
        }
        return { isValid: true, errorMessage: null };
    },

    getSummary: ({ applyToAllServers, applyToAllUsers, selectedMcpServers, selectedAgentServers, selectedBrowserLlms, negatedAgentServers, negatedMcpServers, negatedLlmServers, mcpServers, agentServers, browserLlmServers, applyOnRequest, applyOnResponse, policyBehaviour, targetTags, targetDeviceIds, targetUserNames, negatedTargetTags, negatedTargetDeviceIds, negatedTargetUserNames, skipEnterpriseAccounts }) => {
        const appSettings = (applyOnRequest || applyOnResponse) ?
            ` - ${applyOnRequest ? 'Req' : ''}${applyOnRequest && applyOnResponse ? '/' : ''}${applyOnResponse ? 'Res' : ''}` : '';
        const behaviourSuffix = policyBehaviour ? `Rule behaviour: ${policyBehaviour}` : '';
        let summary = '';
        if (applyToAllServers) {
            summary += 'All assets';
        } else {
            const parts = [
                scopePart(negatedMcpServers, selectedMcpServers, 'MCP'),
                scopePart(negatedAgentServers, selectedAgentServers, 'Agents'),
                scopePart(negatedLlmServers, selectedBrowserLlms, 'LLMs'),
            ].filter(Boolean);
            summary += parts.join(', ') || 'No assets';
        }
        if (applyToAllUsers) {
            summary += ' | All users';
        } else {
            const userScope = describeUserScope({ targetTags, negatedTargetTags, targetDeviceIds, negatedTargetDeviceIds, targetUserNames, negatedTargetUserNames });
            if (userScope) summary += ` | ${userScope}`;
        }
        if (skipEnterpriseAccounts) summary += ' | Personal accounts only';
        summary += `${appSettings} ${behaviourSuffix}`;
        return summary;
    }
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
    negatedAgentServers,
    setNegatedAgentServers,
    negatedMcpServers,
    setNegatedMcpServers,
    negatedLlmServers,
    setNegatedLlmServers,
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
    applyToAllUsers,
    setApplyToAllUsers,
    deviceList = [],
    showConditionError = false,
    showUserConditionError = false,
    skipEnterpriseAccounts,
    setSkipEnterpriseAccounts,
}) => {
    const isBlockMode = policyBehaviour === 'block';
    const isAtlas = isEndpointSecurityCategory();

    const [agenticConditions, agenticDispatch] = useReducer(conditionsReducer, null, () => {
        const conds = [];
        // A negated row with zero values ("Exclude nothing") is meaningful — keep it, don't drop it on reopen
        if ((selectedAgentServers || []).length > 0 || negatedAgentServers) conds.push({ type: 'AGENT', values: selectedAgentServers, otherValues: [], negated: negatedAgentServers });
        if ((selectedMcpServers || []).length > 0 || negatedMcpServers) conds.push({ type: 'MCP_SERVER', values: selectedMcpServers, otherValues: [], negated: negatedMcpServers });
        if ((selectedBrowserLlms || []).length > 0 || negatedLlmServers) conds.push({ type: 'LLM', values: selectedBrowserLlms, otherValues: [], negated: negatedLlmServers });
        return conds;
    });

    useEffect(() => {
        setSelectedAgentServers(agenticConditions.filter(c => c.type === 'AGENT').flatMap(c => c.values).filter(Boolean));
        setSelectedMcpServers(agenticConditions.filter(c => c.type === 'MCP_SERVER').flatMap(c => c.values).filter(Boolean));
        setSelectedBrowserLlms(agenticConditions.filter(c => c.type === 'LLM').flatMap(c => c.values).filter(Boolean));
        // negatedX isn't synced here — it's owned by the Include/Exclude tab; syncing it here would stomp the tab's choice on every value change
    }, [agenticConditions]);



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


    // A selected/excluded value not currently in the live options list (e.g. offline right now) still needs a row so it renders checked, not silently invisible.
    // The wildcard sentinel isn't a real value — never give it its own row.
    const withMissingSelected = (options, selected) => {
        const known = new Set(options.map(o => o.value));
        const missing = (selected || []).filter(v => v !== ALL_VALUES_SENTINEL && !known.has(v)).map(v => ({ value: v, label: v }));
        return missing.length ? [...options, ...missing] : options;
    };

    // Atlas: filterCollections() already groups by service/platform key — use directly.
    // Argus: raw per-device list with disabled state for block mode.
    const mcpOptions = isAtlas
        ? sortSelectedFirst(withMissingSelected(mcpServers || [], selectedMcpServers), selectedMcpServers)
        : sortSelectedFirst(
            withMissingSelected(isBlockMode ? (mcpServers || []).map(s => ({ ...s, disabled: !s.isInline })) : (mcpServers || []), selectedMcpServers),
            selectedMcpServers
          );
    const agentOptions = isAtlas
        ? sortSelectedFirst(withMissingSelected(agentServers || [], selectedAgentServers), selectedAgentServers)
        : sortSelectedFirst(
            withMissingSelected(isBlockMode ? (agentServers || []).map(s => ({ ...s, disabled: !s.isInline })) : (agentServers || []), selectedAgentServers),
            selectedAgentServers
          );
    const llmOptions = isAtlas
        ? sortSelectedFirst(withMissingSelected(browserLlmServers || [], selectedBrowserLlms), selectedBrowserLlms)
        : sortSelectedFirst(
            withMissingSelected(isBlockMode ? (browserLlmServers || []).map(s => ({ ...s, disabled: !s.isInline })) : (browserLlmServers || []), selectedBrowserLlms),
            selectedBrowserLlms
          );

    // Agentic Assets: AI Agents / MCP Servers / LLMs.
    usePruneStaleConditionValues(
        isAtlas,
        agentOptions.length + mcpOptions.length + llmOptions.length > 0,
        agenticConditions, agenticDispatch,
        {
            AGENT: new Set(agentOptions.map(o => o.value)),
            MCP_SERVER: new Set(mcpOptions.map(o => o.value)),
            LLM: new Set(llmOptions.map(o => o.value)),
        }
    );


    const agenticTypeOptions = [
        { label: `${agentOptions.length > 1 ? 'AI Agents' : 'AI Agent'} [${agentOptions.length}]`, value: 'AGENT', disabled: agentOptions.length === 0 },
        { label: `${mcpOptions.length > 1 ? 'MCP Servers' : 'MCP Server'} [${mcpOptions.length}]`, value: 'MCP_SERVER', disabled: mcpOptions.length === 0 },
        { label: `LLM${llmOptions.length > 1 ? 's' : ''} [${llmOptions.length}]`, value: 'LLM', disabled: llmOptions.length === 0 },
    ];


    // Include/Exclude is independent per row (see negatedAgentServers/negatedMcpServers/negatedLlmServers on GuardrailPolicies); an empty Exclude row is the one case it can't stay type-scoped
    const agenticNegationProps = (type) => {
        switch (type) {
            case 'AGENT': return { negated: negatedAgentServers, onToggle: setNegatedAgentServers };
            case 'MCP_SERVER': return { negated: negatedMcpServers, onToggle: setNegatedMcpServers };
            case 'LLM': return { negated: negatedLlmServers, onToggle: setNegatedLlmServers };
            default: return null;
        }
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
        // Guard: don't run until options have loaded — an empty options list would
        // incorrectly clear every pre-selected value from a loaded policy.
        if (!(mcpServers?.length || agentServers?.length || browserLlmServers?.length)) return;
        // The Include wildcard sentinel isn't a real server value — never filter it out here.
        if (selectedMcpServers?.length > 0) {
            const compatible = selectedMcpServers.filter(val => val === ALL_VALUES_SENTINEL || (mcpServers || []).find(s => s.value === val && s.isInline));
            if (compatible.length !== selectedMcpServers.length) setSelectedMcpServers(compatible);
        }
        if (selectedAgentServers?.length > 0) {
            const compatible = selectedAgentServers.filter(val => val === ALL_VALUES_SENTINEL || (agentServers || []).find(s => s.value === val && s.isInline));
            if (compatible.length !== selectedAgentServers.length) setSelectedAgentServers(compatible);
        }
        if (selectedBrowserLlms?.length > 0) {
            const compatible = selectedBrowserLlms.filter(val => val === ALL_VALUES_SENTINEL || (browserLlmServers || []).find(s => s.value === val && s.isInline));
            if (compatible.length !== selectedBrowserLlms.length) setSelectedBrowserLlms(compatible);
        }
    }, [policyBehaviour, mcpServers?.length, agentServers?.length, browserLlmServers?.length]);

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
                                            if (nonZeroItems.length === 0) return collectionsLoading ? "Loading agentic assets…" : "No agentic assets found.";
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
                                            <ConditionRows conditions={agenticConditions} typeOptions={agenticTypeOptions} dispatch={agenticDispatch} operator="OR" showError={showConditionError} getNegationProps={agenticNegationProps} getOptionsForType={getOptionsForType} />
                                        </Box>
                                    )}
                                </VerticalStack>
                            </VerticalStack>
                        </Box>
                    </Box>
                )}
                {isAtlas && (
                    <DeviceUserTargetingCard
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
                        availableTagKeyValues={availableTagKeyValues}
                        availableDevices={availableDevices}
                        availableUsers={availableUsers}
                        matchingDeviceRows={matchingDeviceRows}
                        usersLoading={usersLoading}
                        deviceList={deviceList}
                        showUserConditionError={showUserConditionError}
                    />
                )}
                {isAtlas && (
                    <Box borderColor="border" borderWidth="1" borderRadius="2" background="bg-surface">
                        <Box padding="4">
                            <VerticalStack gap="4">
                                <VerticalStack gap="1">
                                    <HorizontalStack gap="2" blockAlign="center">
                                        <Text variant="headingSm">Work & Personal Accounts</Text>
                                        <Badge status="info">Beta</Badge>
                                    </HorizontalStack>
                                    <Text variant="bodyMd" tone="subdued">
                                        Apply this policy based on the account used to sign in to AI tools.
                                    </Text>
                                </VerticalStack>
                                <VerticalStack gap="2">
                                    <RadioButton
                                        label={
                                            <HorizontalStack gap="2" blockAlign="center">
                                                <Text variant="bodyMd">All accounts</Text>
                                                <Badge tone="success">Recommended</Badge>
                                            </HorizontalStack>
                                        }
                                        helpText="Applies to both work and personal accounts."
                                        checked={!skipEnterpriseAccounts}
                                        id="account_scope_all"
                                        name="accountScope"
                                        onChange={() => setSkipEnterpriseAccounts(false)}
                                    />
                                    <RadioButton
                                        label="Personal accounts only"
                                        helpText="Applies to personal accounts only. Work accounts are excluded."
                                        checked={!!skipEnterpriseAccounts}
                                        id="account_scope_personal"
                                        name="accountScope"
                                        onChange={() => setSkipEnterpriseAccounts(true)}
                                    />
                                </VerticalStack>
                                {skipEnterpriseAccounts && (
                                    <Banner tone="info">Supported for chatgpt.com, claude.ai and grok.com through the Akto browser extension, and the ChatGPT app through Endpoint Shield.</Banner>
                                )}
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
                                            if (nonZeroItems.length === 0) return collectionsLoading ? "Loading agentic assets…" : "No agentic assets found.";
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
                                            <ConditionRows conditions={agenticConditions} typeOptions={agenticTypeOptions} dispatch={agenticDispatch} operator="OR" showError={showConditionError} getNegationProps={agenticNegationProps} getOptionsForType={getOptionsForType} />
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
                                label="Apply guardrail to requests"
                                checked={applyOnRequest}
                                onChange={setApplyOnRequest}
                                helpText="When enabled, this guardrail will filter and evaluate user inputs before they're processed by the model."
                            />

                            <Checkbox
                                label="Apply guardrail to responses"
                                checked={applyOnResponse}
                                onChange={setApplyOnResponse}
                                helpText="When enabled, this guardrail will filter and evaluate model responses before they're sent to users."
                            />
                        </VerticalStack>

                        <Banner tone="info">Response Guardrails are not supported for browser LLMs</Banner>
                    </VerticalStack>
                </Box>
            </FormLayout>
        </VerticalStack>
    );
};

export default ServerSettingsStep;
