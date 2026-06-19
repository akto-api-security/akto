import { VerticalStack, Text, FormLayout, Box, Checkbox, RadioButton, HorizontalStack, Button, Popover, TextField, Icon, Banner, Tag, Divider } from "@shopify/polaris";
import { SearchMinor } from "@shopify/polaris-icons";
import { useState, useEffect, useCallback } from "react";
import DropdownSearch from "../../../../components/shared/DropdownSearch";
import OwaspTag from "../OwaspTag";
import RuleEnforcementDropdown from "../RuleEnforcementDropdown";
import TitleWithInfo from "../../../../components/shared/TitleWithInfo";

export const ServerSettingsConfig = {
    number: 10,
    title: "Server and application settings",

    validate: () => {
        return { isValid: true, errorMessage: null };
    },

    getSummary: ({ applyToAllServers, selectedMcpServers, selectedAgentServers, selectedBrowserLlms, mcpServers, agentServers, browserLlmServers, applyOnRequest, applyOnResponse, policyBehaviour, targetTeams, targetRoles }) => {
        const appSettings = (applyOnRequest || applyOnResponse) ?
            ` - ${applyOnRequest ? 'Req' : ''}${applyOnRequest && applyOnResponse ? '/' : ''}${applyOnResponse ? 'Res' : ''}` : '';
        const behaviourSuffix = policyBehaviour ? `Rule behaviour: ${policyBehaviour}` : '';
        let summary = '';
        if (selectedMcpServers?.length > 0 || selectedAgentServers?.length > 0 || selectedBrowserLlms?.length > 0) {
            const serverSummary = [];
            if (selectedMcpServers?.length > 0) {
                const mcpNames = selectedMcpServers
                    .map(serverId => {
                        const server = mcpServers?.find(s => s.value === serverId);
                        return server ? server.label : serverId;
                    })
                    .slice(0, 2);
                const mcpMore = selectedMcpServers.length > 2 ? ` +${selectedMcpServers.length - 2}` : '';
                serverSummary.push(`MCP: ${mcpNames.join(", ")}${mcpMore}`);
            }
            if (selectedAgentServers?.length > 0) {
                const agentNames = selectedAgentServers
                    .map(serverId => {
                        const server = agentServers?.find(s => s.value === serverId);
                        return server ? server.label : serverId;
                    })
                    .slice(0, 2);
                const agentMore = selectedAgentServers.length > 2 ? ` +${selectedAgentServers.length - 2}` : '';
                serverSummary.push(`Agent: ${agentNames.join(", ")}${agentMore}`);
            }
            if (selectedBrowserLlms?.length > 0) {
                const browserNames = selectedBrowserLlms
                    .map(serverId => {
                        const server = browserLlmServers?.find(s => s.value === serverId);
                        return server ? server.label : serverId;
                    })
                    .slice(0, 2);
                const browserMore = selectedBrowserLlms.length > 2 ? ` +${selectedBrowserLlms.length - 2}` : '';
                serverSummary.push(`Browser LLM: ${browserNames.join(", ")}${browserMore}`);
            }
            summary = `${serverSummary.join(", ")}`;
        }
        summary += `${appSettings} ${behaviourSuffix}`;
        if(applyToAllServers) {
            summary += ` All servers:  true`;
        }
        if (targetTeams?.length > 0) summary += ` Teams: ${targetTeams.slice(0, 2).join(", ")}${targetTeams.length > 2 ? ` +${targetTeams.length - 2}` : ''}`;
        if (targetRoles?.length > 0) summary += ` Roles: ${targetRoles.slice(0, 2).join(", ")}${targetRoles.length > 2 ? ` +${targetRoles.length - 2}` : ''}`;
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
}) => {
    const [serverInfoPopoverActive, setServerInfoPopoverActive] = useState(false);
    const [serverSearchQuery, setServerSearchQuery] = useState('');

    const isBlockMode = policyBehaviour === 'block';

    const compatibleMcpServers = isBlockMode
        ? (mcpServers || []).filter(s => s.isInline)
        : (mcpServers || []);
    const compatibleAgentServers = isBlockMode
        ? (agentServers || []).filter(s => s.isInline)
        : (agentServers || []);
    const compatibleBrowserLlmServers = isBlockMode
        ? (browserLlmServers || []).filter(s => s.isInline)
        : (browserLlmServers || []);
    const totalCompatibleCount = compatibleMcpServers.length + compatibleAgentServers.length + compatibleBrowserLlmServers.length;

    const mcpServersWithDisabled = isBlockMode
        ? (mcpServers || []).map(s => ({ ...s, disabled: !s.isInline }))
        : (mcpServers || []);
    const agentServersWithDisabled = isBlockMode
        ? (agentServers || []).map(s => ({ ...s, disabled: !s.isInline }))
        : (agentServers || []);
    const browserLlmServersWithDisabled = isBlockMode
        ? (browserLlmServers || []).map(s => ({ ...s, disabled: !s.isInline }))
        : (browserLlmServers || []);

    const hasIncompatibleServers = isBlockMode && (
        (mcpServers || []).some(s => !s.isInline) ||
        (agentServers || []).some(s => !s.isInline) ||
        (browserLlmServers || []).some(s => !s.isInline)
    );

    useEffect(() => {
        if (!isBlockMode) return;
        if (selectedMcpServers?.length > 0) {
            const compatible = selectedMcpServers.filter(val =>
                (mcpServers || []).find(s => s.value === val && s.isInline)
            );
            if (compatible.length !== selectedMcpServers.length) {
                setSelectedMcpServers(compatible);
            }
        }
        if (selectedAgentServers?.length > 0) {
            const compatible = selectedAgentServers.filter(val =>
                (agentServers || []).find(s => s.value === val && s.isInline)
            );
            if (compatible.length !== selectedAgentServers.length) {
                setSelectedAgentServers(compatible);
            }
        }
        if (selectedBrowserLlms?.length > 0) {
            const compatible = selectedBrowserLlms.filter(val =>
                (browserLlmServers || []).find(s => s.value === val && s.isInline)
            );
            if (compatible.length !== selectedBrowserLlms.length) {
                setSelectedBrowserLlms(compatible);
            }
        }
    }, [policyBehaviour]);


    const handleClosePopover = useCallback(() => {
        setServerInfoPopoverActive(false);
        setServerSearchQuery('');
    }, []);

    const searchLower = serverSearchQuery.toLowerCase();
    const filteredPopoverMcp = compatibleMcpServers.filter(s =>
        s.label.toLowerCase().includes(searchLower)
    );
    const filteredPopoverAgent = compatibleAgentServers.filter(s =>
        s.label.toLowerCase().includes(searchLower)
    );
    const filteredPopoverBrowserLlm = compatibleBrowserLlmServers.filter(s =>
        s.label.toLowerCase().includes(searchLower)
    );

    return (
        <VerticalStack gap="4">
            <Text variant="bodyMd" tone="subdued">
                Configure which servers the guardrail should be applied to and specify whether it applies to requests, responses, or both.
            </Text>
            <OwaspTag stepNumber={10} />

            <FormLayout>
                <Box padding="4" borderColor="border" borderWidth="1" borderRadius="2" background="bg-surface">
                    <VerticalStack gap="3">
                        <Text variant="headingSm">Server targeting</Text>
                        <VerticalStack gap="2">
                            <Box>
                                <RadioButton
                                    label={
                                        <TitleWithInfo
                                            titleComp={<Text variant="bodyMd">Apply to all</Text>}
                                            tooltipContent="Policy will be applied to all servers that are currently detected and any newly detected servers."
                                        />
                                    }
                                    checked={applyToAllServers === true}
                                    id="apply_to_all_servers"
                                    name="serverTargeting"
                                    onChange={() => setApplyToAllServers(true)}
                                />
                            </Box>

                            {applyToAllServers === true && (
                                <Box paddingInlineStart="6" paddingBlockEnd="1">
                                    <HorizontalStack gap="1" blockAlign="center">
                                        <Text variant="bodyMd" tone="subdued">Applied to</Text>
                                        <Popover
                                            active={serverInfoPopoverActive}
                                            activator={
                                                <Button
                                                    plain
                                                    onClick={() => setServerInfoPopoverActive(v => !v)}
                                                >
                                                    {totalCompatibleCount} server{totalCompatibleCount !== 1 ? 's' : ''}
                                                </Button>
                                            }
                                            onClose={handleClosePopover}
                                            preferredPosition="below"
                                        >
                                            <Popover.Pane fixed>
                                                <div style={{ padding: '12px', minWidth: '260px' }}>
                                                    <VerticalStack gap="3">
                                                        <TextField
                                                            placeholder="Search servers..."
                                                            value={serverSearchQuery}
                                                            onChange={setServerSearchQuery}
                                                            prefix={<Icon source={SearchMinor} />}
                                                            autoComplete="off"
                                                        />
                                                        <div style={{ maxHeight: '220px', overflowY: 'auto' }}>
                                                            <VerticalStack gap="3">
                                                                {filteredPopoverMcp.length > 0 && (
                                                                    <VerticalStack gap="2">
                                                                        <Text variant="bodySm" fontWeight="semibold" tone="subdued">
                                                                            MCP Servers ({filteredPopoverMcp.length})
                                                                        </Text>
                                                                        <div style={{ display: 'flex', flexWrap: 'wrap', gap: '6px' }}>
                                                                            {filteredPopoverMcp.map(s => (
                                                                                <Tag key={s.value}>{s.label}</Tag>
                                                                            ))}
                                                                        </div>
                                                                    </VerticalStack>
                                                                )}
                                                                {filteredPopoverMcp.length > 0 && filteredPopoverAgent.length > 0 && (
                                                                    <Divider />
                                                                )}
                                                                {filteredPopoverAgent.length > 0 && (
                                                                    <VerticalStack gap="2">
                                                                        <Text variant="bodySm" fontWeight="semibold" tone="subdued">
                                                                            Agent Servers ({filteredPopoverAgent.length})
                                                                        </Text>
                                                                        <div style={{ display: 'flex', flexWrap: 'wrap', gap: '6px' }}>
                                                                            {filteredPopoverAgent.map(s => (
                                                                                <Tag key={s.value}>{s.label}</Tag>
                                                                            ))}
                                                                        </div>
                                                                    </VerticalStack>
                                                                )}
                                                                {(filteredPopoverMcp.length > 0 || filteredPopoverAgent.length > 0) && filteredPopoverBrowserLlm.length > 0 && (
                                                                    <Divider />
                                                                )}
                                                                {filteredPopoverBrowserLlm.length > 0 && (
                                                                    <VerticalStack gap="2">
                                                                        <Text variant="bodySm" fontWeight="semibold" tone="subdued">
                                                                            Browser LLMs ({filteredPopoverBrowserLlm.length})
                                                                        </Text>
                                                                        <div style={{ display: 'flex', flexWrap: 'wrap', gap: '6px' }}>
                                                                            {filteredPopoverBrowserLlm.map(s => (
                                                                                <Tag key={s.value}>{s.label}</Tag>
                                                                            ))}
                                                                        </div>
                                                                    </VerticalStack>
                                                                )}
                                                                {filteredPopoverMcp.length === 0 && filteredPopoverAgent.length === 0 && filteredPopoverBrowserLlm.length === 0 && (
                                                                    <Text variant="bodyMd" tone="subdued">No servers found</Text>
                                                                )}
                                                            </VerticalStack>
                                                        </div>
                                                    </VerticalStack>
                                                </div>
                                            </Popover.Pane>
                                        </Popover>
                                        {isBlockMode && (
                                            <Text variant="bodySm" tone="subdued">(inline mode only)</Text>
                                        )}
                                    </HorizontalStack>
                                </Box>
                            )}

                            <Box>
                                <RadioButton
                                    label="Edit servers"
                                    checked={applyToAllServers === false}
                                    id="edit_servers"
                                    name="serverTargeting"
                                    onChange={() => setApplyToAllServers(false)}
                                />
                            </Box>
                        </VerticalStack>

                        {applyToAllServers === false && (
                            <VerticalStack gap="3">
                                {hasIncompatibleServers && (
                                    <Banner tone="info">
                                        <Text variant="bodyMd">
                                            Some servers are disabled — block mode requires servers running in inline (sync) mode.
                                        </Text>
                                    </Banner>
                                )}
                                <DropdownSearch
                                    label="Select MCP Servers"
                                    placeholder="Choose MCP servers where guardrail should be applied"
                                    optionsList={mcpServersWithDisabled}
                                    setSelected={setSelectedMcpServers}
                                    preSelected={selectedMcpServers}
                                    allowMultiple={true}
                                    showSelectAllMinOptions={isBlockMode ? 99999 : 1}
                                    disabled={collectionsLoading}
                                    value={selectedMcpServers.length > 0 ? `${selectedMcpServers.length} MCP server${selectedMcpServers.length === 1 ? "" : "s"} selected` : undefined}
                                />
                                <DropdownSearch
                                    label="Select Agent Servers"
                                    placeholder="Choose agent servers where guardrail should be applied"
                                    optionsList={agentServersWithDisabled}
                                    setSelected={setSelectedAgentServers}
                                    preSelected={selectedAgentServers}
                                    allowMultiple={true}
                                    showSelectAllMinOptions={isBlockMode ? 99999 : 1}
                                    disabled={collectionsLoading}
                                    value={selectedAgentServers.length > 0 ? `${selectedAgentServers.length} agent server${selectedAgentServers.length === 1 ? "" : "s"} selected` : undefined}
                                />
                                <DropdownSearch
                                    label="Select Browser LLMs"
                                    placeholder="Choose browser LLMs where guardrail should be applied"
                                    optionsList={browserLlmServersWithDisabled}
                                    setSelected={setSelectedBrowserLlms}
                                    preSelected={selectedBrowserLlms}
                                    allowMultiple={true}
                                    showSelectAllMinOptions={isBlockMode ? 99999 : 1}
                                    disabled={collectionsLoading}
                                    value={selectedBrowserLlms.length > 0 ? `${selectedBrowserLlms.length} browser LLM${selectedBrowserLlms.length === 1 ? "" : "s"} selected` : undefined}
                                />
                            </VerticalStack>
                        )}
                    </VerticalStack>
                </Box>

                {applyToAllServers && (
                    <Box padding="4" borderColor="border" borderWidth="1" borderRadius="2" background="bg-surface">
                        <VerticalStack gap="3">
                            <Text variant="headingSm">User targeting</Text>
                            <DropdownSearch
                                label="Teams"
                                placeholder="Choose teams"
                                optionsList={(availableTeams || []).map(t => ({ label: t, value: t }))}
                                setSelected={setTargetTeams}
                                preSelected={targetTeams}
                                allowMultiple={true}
                                disabled={usersLoading}
                                value={targetTeams?.length > 0 ? `${targetTeams.length} team${targetTeams.length === 1 ? "" : "s"} selected` : undefined}
                            />
                            <DropdownSearch
                                label="Roles"
                                placeholder="Choose roles"
                                optionsList={(availableRoles || []).map(r => ({ label: r, value: r }))}
                                setSelected={setTargetRoles}
                                preSelected={targetRoles}
                                allowMultiple={true}
                                disabled={usersLoading}
                                value={targetRoles?.length > 0 ? `${targetRoles.length} role${targetRoles.length === 1 ? "" : "s"} selected` : undefined}
                            />
                            {(() => {
                                const hasTeams = targetTeams?.length > 0;
                                const hasRoles = targetRoles?.length > 0;
                                if (!hasTeams && !hasRoles) {
                                    return (
                                        <Text variant="bodySm" tone="subdued">
                                            No restriction — applies to all users across all servers.
                                        </Text>
                                    );
                                }
                                if (hasTeams && hasRoles) {
                                    return (
                                        <Text variant="bodySm" tone="subdued">
                                            Applies to all servers, but only for users in the selected team(s) <strong>and</strong> with the selected role(s). Each selection narrows the target independently.
                                        </Text>
                                    );
                                }
                                if (hasTeams) {
                                    return (
                                        <Text variant="bodySm" tone="subdued">
                                            Applies to all servers, but only for devices belonging to the selected team{targetTeams.length > 1 ? "s" : ""}.
                                        </Text>
                                    );
                                }
                                return (
                                    <Text variant="bodySm" tone="subdued">
                                        Applies to all servers, but only for devices belonging to users with the selected role{targetRoles.length > 1 ? "s" : ""}.
                                    </Text>
                                );
                            })()}
                        </VerticalStack>
                    </Box>
                )}

                <Box padding="4" borderColor="border" borderWidth="1" borderRadius="2" background="bg-surface">
                    <VerticalStack gap="3">
                        <RuleEnforcementDropdown
                            id="policy-rule-behaviour"
                            value={policyBehaviour}
                            onChange={setPolicyBehaviour}
                        />
                    </VerticalStack>
                </Box>

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
