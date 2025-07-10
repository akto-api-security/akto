import { Box, Tag, HorizontalStack, Avatar, Link, Button, Text } from '@shopify/polaris';


function JiraTicketDisplay({ jiraTicketUrl, jiraKey, onButtonClick, ariaLabel }) {
    return (
        <Box onClick={(e) => e.stopPropagation()}>
            {jiraKey && jiraTicketUrl ? (
                <Tag>
                    <HorizontalStack gap={1}>
                        <Avatar size="extraSmall" shape='round' source="/public/logo_jira.svg" />
                        <Link url={jiraTicketUrl} target="_blank">
                            <Text>{jiraKey}</Text>
                        </Link>
                    </HorizontalStack>
                </Tag>
            ) : (
                <Button
                    plain
                    onClick={onButtonClick} 
                    aria-label={ariaLabel || "Create Jira ticket"}
                >
                    <Avatar size="extraSmall" shape="round" source="/public/logo_jira.svg" />
                </Button>
            )}
        </Box>
    );
}

export default JiraTicketDisplay;