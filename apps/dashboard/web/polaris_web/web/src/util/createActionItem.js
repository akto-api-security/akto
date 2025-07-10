import { Box, Badge, HorizontalStack } from '@shopify/polaris';
import TooltipText from '../apps/dashboard/components/shared/TooltipText';
import JiraTicketDisplay from '../apps/dashboard/components/shared/JiraTicketDisplay';
import { ExternalMinor } from '@shopify/polaris-icons';

export function createActionItem(id, priority, title, description, team, effort, count, actionItemType, jiraTicketUrlMap, handleJiraIntegration) {
    const actionItemObj = { id, title, description, actionItemType, team, effort, count };
    return {
        id,
        priority: (
            <Box style={{ display: 'flex', alignItems: 'center', height: '24px', padding: '2px 0' }}>
                <Badge status={priority === 'P1' ? 'critical' : 'attention'}>{priority}</Badge>
            </Box>
        ),
        priorityComp: <Badge status={priority === 'P1' ? 'critical' : 'attention'}>{priority}</Badge>,
        actionItem: (
            <Box maxWidth="400px">
                <TooltipText tooltip={title} text={title} textProps={{ fontWeight: 'medium' }} />
            </Box>
        ),
        team: (
            <Box maxWidth="200px">
                <TooltipText tooltip={team} text={team} />
            </Box>
        ),
        effort: (
            <Box maxWidth="100px">
                <TooltipText tooltip={effort} text={effort} />
            </Box>
        ),
        whyItMatters: (
            <Box maxWidth="400px">
                <TooltipText tooltip={description} text={description} />
            </Box>
        ),
        displayName: title,
        title: title,
        description: description,
        actionItemType: actionItemType,
        actions: (
            <Box style={{ display: 'flex', alignItems: 'center', height: '24px', padding: '2px 0' }}>
                <HorizontalStack gap="2" align="center">
                    <JiraTicketDisplay
                        jiraTicketUrl={jiraTicketUrlMap[actionItemType]}
                        jiraKey={jiraTicketUrlMap[actionItemType] ? jiraTicketUrlMap[actionItemType].split('/').pop() : ""}
                        onButtonClick={() => handleJiraIntegration(actionItemObj)}
                    />
                </HorizontalStack>
            </Box>
        ),
        count: count,
        actionItemObj: actionItemObj
    };
} 