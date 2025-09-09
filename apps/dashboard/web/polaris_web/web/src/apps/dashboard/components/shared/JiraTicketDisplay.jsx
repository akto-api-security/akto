import { Box, Tag, HorizontalStack, Avatar, Link, Button, Text } from '@shopify/polaris';

function JiraTicketDisplay({ jiraTicketUrl, jiraKey, onButtonClick, ariaLabel }) {
    return (
        <Box onClick={(e) => e.stopPropagation()}>
            {jiraKey && jiraTicketUrl ? (
                <Tag>
                    <HorizontalStack gap={1} align="center" wrap={false}>
                        <Box minWidth="16px">
                            <Avatar size="extraSmall" shape="round" source="/public/logo_jira.svg" />
                        </Box>
                        <Box maxWidth="100%" overflowX="hidden" whiteSpace="nowrap">
                            <Link url={jiraTicketUrl} target="_blank">
                                <Text as="span">{jiraKey}</Text>
                            </Link>
                        </Box>
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
