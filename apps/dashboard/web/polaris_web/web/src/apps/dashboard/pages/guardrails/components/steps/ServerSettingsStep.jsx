import { VerticalStack, Text, FormLayout, Box, Checkbox, RadioButton, HorizontalStack, Icon, Tooltip } from "@shopify/polaris";
import { InfoMinor } from "@shopify/polaris-icons";
import DropdownSearch from "../../../../components/shared/DropdownSearch";
import OwaspTag from "../OwaspTag";
import RuleEnforcementDropdown from "../RuleEnforcementDropdown";

export const ServerSettingsConfig = {
    number: 10,
    title: "Server and application settings",

    validate: () => {
        return { isValid: true, errorMessage: null };
    },

    getSummary: ({ applyToAllServers, selectedMcpServers, selectedAgentServers, mcpServers, agentServers, applyOnRequest, applyOnResponse, policyBehaviour }) => {
        const appSettings = (applyOnRequest || applyOnResponse) ?
            ` - ${applyOnRequest ? 'Req' : ''}${applyOnRequest && applyOnResponse ? '/' : ''}${applyOnResponse ? 'Res' : ''}` : '';
        const behaviourSuffix = policyBehaviour ? ` — ${policyBehaviour}` : '';

        if (applyToAllServers) {
            return `All servers${appSettings}${behaviourSuffix}`;
        }

        if (selectedMcpServers?.length > 0 || selectedAgentServers?.length > 0) {
            const serverSummary = [];
            if (selectedMcpServers.length > 0) {
                const mcpNames = selectedMcpServers
                    .map(serverId => {
                        const server = mcpServers?.find(s => s.value === serverId);
                        return server ? server.label : serverId;
                    })
                    .slice(0, 2);
                const mcpMore = selectedMcpServers.length > 2 ? ` +${selectedMcpServers.length - 2}` : '';
                serverSummary.push(`MCP: ${mcpNames.join(", ")}${mcpMore}`);
            }
            if (selectedAgentServers.length > 0) {
                const agentNames = selectedAgentServers
                    .map(serverId => {
                        const server = agentServers?.find(s => s.value === serverId);
                        return server ? server.label : serverId;
                    })
                    .slice(0, 2);
                const agentMore = selectedAgentServers.length > 2 ? ` +${selectedAgentServers.length - 2}` : '';
                serverSummary.push(`Agent: ${agentNames.join(", ")}${agentMore}`);
            }
            return `${serverSummary.join(", ")}${appSettings}${behaviourSuffix}`;
        }
        if (policyBehaviour) {
            return `Rule behaviour: ${policyBehaviour}`;
        }
        return null;
    }
};

const ServerSettingsStep = ({
    applyToAllServers,
    setApplyToAllServers,
    selectedMcpServers,
    setSelectedMcpServers,
    selectedAgentServers,
    setSelectedAgentServers,
    applyOnResponse,
    setApplyOnResponse,
    applyOnRequest,
    setApplyOnRequest,
    mcpServers,
    agentServers,
    collectionsLoading,
    policyBehaviour,
    setPolicyBehaviour
}) => {
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
                            <RadioButton
                                label={
                                    <HorizontalStack gap="1" blockAlign="center">
                                        <span>Apply to all</span>
                                        <Tooltip
                                            content="Policy will be applied to all servers that are currently detected and any newly detected servers."
                                            dismissOnMouseOut
                                        >
                                            <span style={{ display: 'inline-flex', cursor: 'help', lineHeight: 0 }}>
                                                <Icon source={InfoMinor} tone="subdued" />
                                            </span>
                                        </Tooltip>
                                    </HorizontalStack>
                                }
                                checked={applyToAllServers === true}
                                id="apply_to_all_servers"
                                name="serverTargeting"
                                onChange={() => setApplyToAllServers(true)}
                            />
                            <RadioButton
                                label="Edit servers"
                                checked={applyToAllServers === false}
                                id="edit_servers"
                                name="serverTargeting"
                                onChange={() => setApplyToAllServers(false)}
                            />
                        </VerticalStack>

                        {applyToAllServers === false && (
                            <VerticalStack gap="3">
                                <DropdownSearch
                                    label="Select MCP Servers"
                                    placeholder="Choose MCP servers where guardrail should be applied"
                                    optionsList={mcpServers}
                                    setSelected={setSelectedMcpServers}
                                    preSelected={selectedMcpServers}
                                    allowMultiple={true}
                                    showSelectAllMinOptions={1}
                                    disabled={collectionsLoading}
                                />
                                <DropdownSearch
                                    label="Select Agent Servers"
                                    placeholder="Choose agent servers where guardrail should be applied"
                                    optionsList={agentServers}
                                    setSelected={setSelectedAgentServers}
                                    preSelected={selectedAgentServers}
                                    allowMultiple={true}
                                    showSelectAllMinOptions={1}
                                    disabled={collectionsLoading}
                                />
                            </VerticalStack>
                        )}
                    </VerticalStack>
                </Box>

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
