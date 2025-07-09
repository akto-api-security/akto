import { Avatar, Badge, Box, Button, Card, Divider, HorizontalStack, Icon, Text, VerticalStack, Tag, Link } from '@shopify/polaris'
import { TeamMajor, ToolsMajor } from "@shopify/polaris-icons"
import TooltipText from '../../../components/shared/TooltipText'
import React, { useMemo } from 'react'
import JiraTicketDisplay from '../../../components/shared/JiraTicketDisplay';

function ActionItemCard(props) {
    const { cardObj, onButtonClick, jiraTicketUrlMap = {} } = props;

    const jiraTicketUrl = jiraTicketUrlMap[cardObj.actionItemType];
    const jiraKey = jiraTicketUrl && jiraTicketUrl.length > 0
        ? /[^/]*$/.exec(jiraTicketUrl)[0]
        : "";

    const getPriorityStatus = (priority) => {
        const statusMap = {
            P0: 'critical-strong-experimental',
            P1: 'critical',
            P2: 'attention',
            P3: 'warning',
            P4: 'info',
            P5: 'success'
        };
        return statusMap[priority] || 'new';
    };

    return (
        <Card padding="5">
            <VerticalStack gap="3">
                <Box width="30px">
                    <Badge status={getPriorityStatus(cardObj.priority)}>
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

                <HorizontalStack gap="2">
                    <HorizontalStack gap="1">
                        <Icon source={TeamMajor} color="subdued" />
                        <Text variant="bodyMd">{cardObj.team}</Text>
                    </HorizontalStack>
                    <HorizontalStack gap="1">
                        <Icon source={ToolsMajor} color="subdued" />
                        <Text variant="bodyMd">{cardObj.effort}</Text>
                    </HorizontalStack>
                </HorizontalStack>

                <Divider />

                <HorizontalStack gap="3" align="space-between">
                    <JiraTicketDisplay
                        jiraTicketUrl={jiraTicketUrl}
                        jiraKey={jiraKey}
                        onButtonClick={() => onButtonClick?.(cardObj)}
                    />
                </HorizontalStack>
            </VerticalStack>
        </Card>
    );
}

export default ActionItemCard;