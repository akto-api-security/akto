import { Box, Badge, HorizontalStack } from '@shopify/polaris';
import { ExternalMinor } from '@shopify/polaris-icons';
import TooltipText from '../apps/dashboard/components/shared/TooltipText';
import JiraTicketDisplay from '../apps/dashboard/components/shared/JiraTicketDisplay';

export function createActionItem(
    id,
    priority,
    title,
    description,
    team,
    effort,
    count,
    actionItemType,
    jiraTicketUrlMap,
    handleJiraIntegration
) {
    const jiraTicketUrl = jiraTicketUrlMap[actionItemType];
    const jiraKey = jiraTicketUrl ? jiraTicketUrl.split('/').pop() : '';

    const actionItemObj = {
        id,
        title,
        description,
        actionItemType,
        team,
        effort,
        count
    };

    const renderTooltipBox = (text, width) => (
        <Box maxWidth={width}>
            <TooltipText tooltip={text} text={text} />
        </Box>
    );

    const badgeStatus = priority === 'P1' ? 'critical' : 'attention';

    return {
        id,
        priority: (
            <Box display="flex" alignItems="center" style={{ height: '24px', padding: '2px 0' }}>
                <Badge status={badgeStatus}>{priority}</Badge>
            </Box>
        ),
        priorityComp: <Badge status={badgeStatus}>{priority}</Badge>,
        actionItem: renderTooltipBox(title, '400px'),
        team: renderTooltipBox(team, '200px'),
        effort: renderTooltipBox(effort, '100px'),
        whyItMatters: renderTooltipBox(description, '400px'),
        displayName: title,
        title,
        description,
        actionItemType,
        count,
        actionItemObj,
        actions: (
            <Box display="flex" alignItems="center" style={{ height: '24px', padding: '2px 0' }}>
                <HorizontalStack gap="2" align="center">
                    <JiraTicketDisplay
                        jiraTicketUrl={jiraTicketUrl}
                        jiraKey={jiraKey}
                        onButtonClick={() => handleJiraIntegration(actionItemObj)}
                    />
                </HorizontalStack>
            </Box>
        )
    };
}
