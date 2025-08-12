import { Divider, Text, VerticalStack, Box } from '@shopify/polaris';
import React from 'react';
import { TeamMajor, ToolsMajor } from '@shopify/polaris-icons';
import FlyoutHeadingComponent from '../../../components/shared/FlyoutHeadingComponent';
import JiraTicketDisplay from '../../../components/shared/JiraTicketDisplay';
import FlyoutTable from './FlyoutTable';
import IssuesBySubCategoryFlyout from './IssuesBySubCategoryFlyout';

function ActionItemDetails({ item, jiraTicketUrlMap = {}, onJiraButtonClick, allApiInfo, apiInfoLoading }) {
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

    const extractPriorityValue = (item) => {
        if (typeof item.priority === 'string') {
            return item.priority;
        }
        if (item.priority?.props?.children?.props?.children) {
            return item.priority.props.children.props.children;
        }
        if (item.actionItemObj?.priority) {
            return item.actionItemObj.priority;
        }
    };

    const priorityValue = extractPriorityValue(item);

    const itemDetails = {
        title: itemData?.title || itemData?.actionItem || itemData?.staticTitle || 'Action Item',
        priority: getPriorityStatus(priorityValue),
        priorityValue: priorityValue,
        moreInfo: [
            {
                icon: TeamMajor,
                text: itemData?.team || 'Platform'
            },
            {
                icon: ToolsMajor,
                text: itemData?.effort || 'Medium'
            },
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
            {itemData?.actionItemType === 'BUILD_REMEDIATION_PLAYBOOKS' ? (
                <IssuesBySubCategoryFlyout urlsByIssues={allApiInfo?.urlsByIssues} />
            ) : (
                <FlyoutTable 
                    actionItemType={itemData?.actionItemType} 
                    count={itemData?.count}
                    allApiInfo={allApiInfo} 
                    apiInfoLoading={apiInfoLoading} 
                />
            )}
        </VerticalStack>
    );
}

export default ActionItemDetails;