import { Box, Tag, HorizontalStack, Avatar, Link, Button, Text } from '@shopify/polaris';
import React from 'react';

function JiraTicketDisplay({ jiraTicketUrl, jiraKey, onButtonClick, ariaLabel }) {
    return (
        <Box>
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
                    onClick={e => {
                        e.stopPropagation();
                        onButtonClick?.();
                    }}
                    aria-label={ariaLabel || "Create Jira ticket"}
                >
                    <Avatar size="extraSmall" shape="round" source="/public/logo_jira.svg" />
                </Button>
            )}
        </Box>
    );
}

export default JiraTicketDisplay; 