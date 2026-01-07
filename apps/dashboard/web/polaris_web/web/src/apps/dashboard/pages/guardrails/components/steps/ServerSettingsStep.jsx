import { VerticalStack, Text, FormLayout, Box, Checkbox } from "@shopify/polaris";
import DropdownSearch from "../../../../components/shared/DropdownSearch";

export const ServerSettingsConfig = {
    number: 8,
    title: "Server and application settings",

    validate: () => {
        return { isValid: true, errorMessage: null };
    },

    getSummary: ({ selectedMcpServers, selectedAgentServers, mcpServers, agentServers, applyOnRequest, applyOnResponse }) => {
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
            const appSettings = (applyOnRequest || applyOnResponse) ?
                ` - ${applyOnRequest ? 'Req' : ''}${applyOnRequest && applyOnResponse ? '/' : ''}${applyOnResponse ? 'Res' : ''}` : '';
            return `${serverSummary.join(", ")}${appSettings}`;
        }
        return null;
    }
};

const ServerSettingsStep = ({
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
    collectionsLoading
}) => {
    return (
        <VerticalStack gap="4">
            <Text variant="headingMd">Server and application settings</Text>
            <Text variant="bodyMd" tone="subdued">
                Configure which servers the guardrail should be applied to and specify whether it applies to requests, responses, or both.
            </Text>

            <FormLayout>
                <DropdownSearch
                    label="Select MCP Servers"
                    placeholder="Choose MCP servers where guardrail should be applied"
                    optionsList={mcpServers}
                    setSelected={setSelectedMcpServers}
                    preSelected={selectedMcpServers}
                    allowMultiple={true}
                    disabled={collectionsLoading}
                />

                <DropdownSearch
                    label="Select Agent Servers"
                    placeholder="Choose agent servers where guardrail should be applied"
                    optionsList={agentServers}
                    setSelected={setSelectedAgentServers}
                    preSelected={selectedAgentServers}
                    allowMultiple={true}
                    disabled={collectionsLoading}
                />

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
