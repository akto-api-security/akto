import { Divider, Text, VerticalStack, Icon, Box } from '@shopify/polaris';
import React from 'react';
import { ClockMajor, TeamMajor, ToolsMajor } from '@shopify/polaris-icons';
import FlyoutHeadingComponent from '../../../components/shared/FlyoutHeadingComponent';
import JiraTicketDisplay from '../../../components/shared/JiraTicketDisplay';

function ActionItemDetails({ item, jiraTicketUrlMap = {}, onJiraButtonClick }) {
    if (!item) return null;

    const itemData = item?.actionItemObj || item;

    const jiraTicketUrl = jiraTicketUrlMap[itemData?.actionItemType];
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

    const itemDetails = {
        title: itemData?.title || itemData?.actionItem || 'Action Item',
        priority: getPriorityStatus(itemData?.priority),
        priorityValue: itemData?.priority || 'P1',
        moreInfo: [
            {
                icon: TeamMajor,
                text: itemData?.team || 'Platform'
            },
            {
                icon: ToolsMajor,
                text: itemData?.effort || 'Medium'
            },
            {
                icon: ClockMajor,
                text: '2 hours ago'
            }
        ],
        secondaryActions: [
            {
                iconComp: () => (
                    <JiraTicketDisplay
                        jiraTicketUrl={jiraTicketUrl}
                        jiraKey={jiraKey}
                        onButtonClick={(e) => {
                            if (e && e.stopPropagation) e.stopPropagation();
                            onJiraButtonClick?.(itemData);
                        }}
                        ariaLabel={jiraTicketUrl ? `View Jira ticket ${jiraKey}` : 'Create Jira ticket'}
                    />
                ),
                onClick: jiraTicketUrl
                    ? () => window.open(jiraTicketUrl, '_blank')
                    : () => onJiraButtonClick?.(itemData)
            }
        ]
    };

    return (
        <VerticalStack gap={"2"}>
            <FlyoutHeadingComponent itemDetails={itemDetails} />
            <Divider borderWidth="1" />
            <Box paddingBlockStart="2">
                <Text variant="bodyMd" color="subdued">
                    {itemData?.description || itemData?.whyItMatters || 'No additional details available.'}
                </Text>
            </Box>
        </VerticalStack>
    );
}

export default ActionItemDetails;