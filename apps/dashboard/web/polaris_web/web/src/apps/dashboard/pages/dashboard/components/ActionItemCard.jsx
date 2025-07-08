import { Avatar, Badge, Box, Button, Card, Divider, HorizontalStack, Icon, Text, VerticalStack, Popover, OptionList, Tag, Link } from '@shopify/polaris'

import React, { useState } from 'react'
import { TeamMajor, ToolsMajor, EmailMajor } from "@shopify/polaris-icons"
import TooltipText from '../../../components/shared/TooltipText'

function ActionItemCard(props) {
    const { cardObj, onButtonClick, jiraTicketUrlMap = {} } = props;

    const jiraTicketUrl = jiraTicketUrlMap[cardObj.actionItemType];
    const jiraKey = jiraTicketUrl && jiraTicketUrl.length > 0 ? jiraTicketUrl.split('/').pop() : "";

    const renderJiraComponent = () => {
        return (
            <Box 
                style={{ 
                    display: 'flex', 
                    alignItems: 'center', 
                    justifyContent: 'flex-start',
                    minHeight: '20px' 
                }}
            >
                {jiraKey ? (
                    <Tag>
                        <HorizontalStack gap={1} align="center">
                            <Avatar size="extraSmall" shape="round" source="/public/logo_jira.svg" />
                            <Link 
                                url={jiraTicketUrl} 
                                target="_blank"
                                onClick={(e) => {
                                    e.stopPropagation();
                                }}
                            >
                                <Text color="base" variant="bodySm">
                                    {jiraKey}
                                </Text>
                            </Link>
                        </HorizontalStack>
                    </Tag>
                ) : (
                    <div
                        onClick={(e) => {
                            e.stopPropagation();
                            if (onButtonClick) {
                                onButtonClick(cardObj);
                            }
                        }}
                        style={{ cursor: 'pointer' }}
                        title="Create Jira Ticket"
                    >
                        <Avatar size="extraSmall" shape="round" source="/public/logo_jira.svg" />
                    </div>
                )}
            </Box>
        );
    };

    return (
        <div
            onClick={e => {
                if (
                    e.target.closest('.Polaris-Button') ||
                    e.target.closest('.Polaris-Popover') ||
                    e.target.closest('.Polaris-Tag') ||
                    e.target.closest('.Polaris-Modal-CloseButton') ||
                    e.target.closest('.jira-button') ||
                    e.target.closest('.jira-link')
                ) {
                    return;
                }
                if (e.cancelBubble) {
                    return;
                }
            }}
            style={{ cursor: 'pointer' }}
        >
            <Card padding={"5"}>
                <VerticalStack gap={"3"}>
                    <Box width='30px'>
                        <Badge status={cardObj.priority === 'P0' ? 'critical-strong-experimental' :
                            cardObj.priority === 'P1' ? 'critical' :
                                cardObj.priority === 'P2' ? 'attention' :
                                    cardObj.priority === 'P3' ? 'warning' :
                                        cardObj.priority === 'P4' ? 'info' :
                                            cardObj.priority === 'P5' ? 'success' :
                                                'new'}>
                            {cardObj.priority}
                        </Badge>
                    </Box>
                    <Box maxWidth="220px">
                        <TooltipText
                            tooltip={cardObj.title}
                            text={cardObj.title}
                            textProps={{ variant: 'headingSm' }}
                        />
                        <TooltipText
                            tooltip={cardObj.description}
                            text={cardObj.description}
                            textProps={{ variant: 'bodyMd', color: 'subdued' }}
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
                            {renderJiraComponent()}
                        </Box>
                        <Box>
                        </Box>
                    </HorizontalStack>
                </VerticalStack>
            </Card>
        </div>
    )
}

export default ActionItemCard