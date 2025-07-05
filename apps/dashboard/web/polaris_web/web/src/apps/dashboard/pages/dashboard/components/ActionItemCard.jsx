import { Avatar, Badge, Box, Button, Card, Divider, HorizontalStack, Icon, Text, VerticalStack, Popover, OptionList, Tag } from '@shopify/polaris'

import React, { useState } from 'react'
import { TeamMajor, ToolsMajor, EmailMajor } from "@shopify/polaris-icons"
import TooltipText from '../../../components/shared/TooltipText'

function ActionItemCard(props) {
    const { cardObj, onButtonClick } = props;
    
    const handleClick = (e) => {
        e.stopPropagation();
        if (!isIntegrated) {
            window.location.href = "/dashboard/settings/integrations/jira";
        } else {
            // Handle Jira integration
            console.log('Create Jira ticket for:', cardObj);
        }
    };

    const isIntegrated = typeof window !== 'undefined' && window.JIRA_INTEGRATED === true;
    
    return (
        <div
            onClick={e => {
                // Prevent flyout if clicking on assign button, popover, or tag
                if (
                    e.target.closest('.Polaris-Button') ||
                    e.target.closest('.Polaris-Popover') ||
                    e.target.closest('.Polaris-Tag') ||
                    e.target.closest('.Polaris-Modal-CloseButton')
                ) {
                    return;
                }
                onButtonClick(cardObj);
            }}
            style={{cursor: 'pointer'}}
        >
        <Card padding={"5"}>
            <VerticalStack gap={"3"}>
                <Box width='30px'>
                    <Badge status="critical-strong-experimental">P0</Badge>
                </Box>
                <Box maxWidth="220px">
                    <TooltipText 
                        tooltip={cardObj.title} 
                        text={cardObj.title} 
                        textProps={{variant: 'headingSm'}} 
                    />
                    <TooltipText 
                        tooltip={cardObj.description} 
                        text={cardObj.description} 
                        textProps={{variant: 'bodyMd', color: 'subdued'}} 
                    />
                </Box>
                <HorizontalStack gap={"2"}>
                    <HorizontalStack gap={"1"}>
                        <Box><Icon source={TeamMajor} color="subdued" /></Box>
                        <Text variant='bodyMd'>{cardObj.team}</Text>
                    </HorizontalStack>
                    <HorizontalStack gap={"1"}>
                        <Box><Icon source={ToolsMajor} color="subdued" /></Box>
                        <Text variant='bodyMd'>{cardObj.effort}</Text>
                    </HorizontalStack>
                </HorizontalStack>
                <Divider />
                    <HorizontalStack gap={"3"} align="space-between" wrap={false}>
                        <Box className="action-item-card-actions">
                            <HorizontalStack gap={"2"}>
                                {/* Email icon button removed */}
                                <button
                                    className="Polaris-Modal-CloseButton"
                                    onClick={handleClick}
                                    title={isIntegrated ? 'Create Jira Ticket' : 'Integrate Jira'}
                                >
                                    <Box className='reduce-size'>
                                        <Avatar size="extraSmall" shape="square" source="/public/logo_jira.svg" />
                                    </Box>
                                </button>
                            </HorizontalStack>
                    </Box>
                    <Box>
                        {/* TODO: Re-enable assign task functionality in future iteration */}
                        {/* {assignedUser ? (
                            <Tag onRemove={() => setSelectedUser([])}>
                                {assignedUser.label}
                            </Tag>
                        ) : (
                            <Popover
                                active={popoverActive}
                                activator={activator}
                                onClose={() => setPopoverActive(false)}
                                autofocusTarget="first-node"
                            >
                                <OptionList
                                    title="Assign to"
                                    onChange={handleUserSelect}
                                    options={users}
                                    selected={selectedUser}
                                />
                            </Popover>
                        )} */}
                    </Box>
                </HorizontalStack>
            </VerticalStack>
        </Card>
        </div>
    )
}

export default ActionItemCard