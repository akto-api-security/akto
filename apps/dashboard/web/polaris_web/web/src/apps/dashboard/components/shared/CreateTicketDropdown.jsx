import React, { useCallback, useState } from 'react';
import { Button, Popover, VerticalStack } from '@shopify/polaris';

/**
 * Reusable Create Ticket Dropdown Component
 *
 * Provides a unified dropdown button for creating tickets across multiple integrations
 * (Jira, Azure Boards, ServiceNow, DevRev). The dropdown automatically detects which
 * integrations are enabled via window flags and only shows available options.
 *
 * @param {Object} props
 * @param {Function} props.onJiraClick - Handler for Jira integration click
 * @param {Function} props.onAzureBoardsClick - Handler for Azure Boards integration click
 * @param {Function} props.onServiceNowClick - Handler for ServiceNow integration click
 * @param {Function} props.onDevRevClick - Handler for DevRev integration click
 * @param {string} props.jiraTicketUrl - Existing Jira ticket URL (disables option if present)
 * @param {string} props.azureBoardsUrl - Existing Azure Boards work item URL (disables option if present)
 * @param {string} props.serviceNowTicketUrl - Existing ServiceNow ticket URL (disables option if present)
 * @param {string} props.devrevWorkUrl - Existing DevRev work URL (disables option if present)
 * @param {Function} props.onOpenPopover - Optional callback when popover opens (use to close other popovers)
 * @param {Function} props.onClosePopover - Optional callback when popover closes
 *
 * @example
 * <CreateTicketDropdown
 *     onJiraClick={handleJiraClick}
 *     onAzureBoardsClick={handleAzureBoardClick}
 *     onServiceNowClick={handleServiceNowClick}
 *     onDevRevClick={handleDevRevClick}
 *     jiraTicketUrl={jiraIssueUrl}
 *     azureBoardsUrl={azureBoardsWorkItemUrl}
 *     serviceNowTicketUrl={serviceNowTicketUrl}
 *     devrevWorkUrl={devrevWorkUrl}
 *     onOpenPopover={() => setOtherPopoverActive(false)}
 * />
 */
const CreateTicketDropdown = ({
    onJiraClick,
    onAzureBoardsClick,
    onServiceNowClick,
    onDevRevClick,
    jiraTicketUrl = "",
    azureBoardsUrl = "",
    serviceNowTicketUrl = "",
    devrevWorkUrl = "",
    onOpenPopover,
    onClosePopover
}) => {
    const [ticketPopoverActive, setTicketPopoverActive] = useState(false);
    const [hoveredIndex, setHoveredIndex] = useState(null);

    // Check if any integration is available (both handler provided AND integration enabled)
    const hasAnyIntegration = (onJiraClick && window.JIRA_INTEGRATED === 'true') ||
                               (onAzureBoardsClick && window.AZURE_BOARDS_INTEGRATED === 'true') ||
                               (onServiceNowClick && window.SERVICENOW_INTEGRATED === 'true') ||
                               (onDevRevClick && window.DEVREV_INTEGRATED === 'true');

    const toggleTicketPopover = useCallback(() => {
        const newState = !ticketPopoverActive;
        setTicketPopoverActive(newState);

        if (newState && onOpenPopover) {
            onOpenPopover();
        }
    }, [ticketPopoverActive, onOpenPopover]);

    const handleClose = useCallback(() => {
        setTicketPopoverActive(false);
        if (onClosePopover) {
            onClosePopover();
        }
    }, [onClosePopover]);

    const handleIntegrationClick = useCallback((clickHandler) => {
        if (clickHandler) {
            clickHandler();
        }
        setTicketPopoverActive(false);
    }, []);

    const ticketIntegrations = [];

    // Only show Jira if handler is provided and integration is enabled
    if (onJiraClick && window.JIRA_INTEGRATED === 'true') {
        ticketIntegrations.push({
            content: 'Jira',
            onAction: () => handleIntegrationClick(onJiraClick),
            disabled: jiraTicketUrl !== ""
        });
    }

    // Only show Azure Boards if handler is provided and integration is enabled
    if (onAzureBoardsClick && window.AZURE_BOARDS_INTEGRATED === 'true') {
        ticketIntegrations.push({
            content: 'Azure Boards',
            onAction: () => handleIntegrationClick(onAzureBoardsClick),
            disabled: azureBoardsUrl !== ""
        });
    }

    // Only show ServiceNow if handler is provided and integration is enabled
    if (onServiceNowClick && window.SERVICENOW_INTEGRATED === 'true') {
        ticketIntegrations.push({
            content: 'ServiceNow',
            onAction: () => handleIntegrationClick(onServiceNowClick),
            disabled: serviceNowTicketUrl !== ""
        });
    }

    // Only show DevRev if handler is provided and integration is enabled
    if (onDevRevClick && window.DEVREV_INTEGRATED === 'true') {
        ticketIntegrations.push({
            content: 'DevRev',
            onAction: () => handleIntegrationClick(onDevRevClick),
            disabled: devrevWorkUrl !== ""
        });
    }

    if (!hasAnyIntegration) {
        return null;
    }

    return (
        <Popover
            activator={
                <Button
                    primary
                    disclosure
                    onClick={toggleTicketPopover}
                >
                    Create Ticket
                </Button>
            }
            active={ticketPopoverActive}
            onClose={handleClose}
            autofocusTarget="first-node"
            preferredPosition="below"
            preferredAlignment="left"
        >
            <Popover.Pane fixed>
                <Popover.Section>
                    <VerticalStack gap={"4"}>
                        {ticketIntegrations.map((integration, index) => {
                            return(
                                <div
                                    style={{
                                        cursor: integration.disabled ? 'not-allowed' : 'pointer',
                                        opacity: integration.disabled ? 0.5 : 1,
                                        padding: '8px 12px',
                                        borderRadius: '4px',
                                        transition: 'background-color 0.2s ease',
                                        backgroundColor: !integration.disabled && hoveredIndex === index ? '#f6f8fa' : 'transparent'
                                    }}
                                    onMouseEnter={() => setHoveredIndex(index)}
                                    onMouseLeave={() => setHoveredIndex(null)}
                                    onClick={() => {
                                        if (!integration.disabled) {
                                            integration.onAction();
                                        }
                                    }}
                                    key={index}
                                >
                                    {integration.content}
                                </div>
                            )
                        })}
                    </VerticalStack>
                </Popover.Section>
            </Popover.Pane>
        </Popover>
    );
};

export default CreateTicketDropdown;
