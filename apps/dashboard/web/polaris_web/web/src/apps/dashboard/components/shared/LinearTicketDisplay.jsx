import { Avatar, HorizontalStack, Link, Tag, Text } from '@shopify/polaris'
import React from 'react'

export const getLinearKey = (linearIssueUrl) => {
    const url = linearIssueUrl || ''
    const issueSegment = /\/issue\/([^/?#]+)/.exec(url)
    return issueSegment ? issueSegment[1] : /[^/]*$/.exec(url)[0]
}

function LinearTicketDisplay({ linearIssueUrl }) {
    if (!linearIssueUrl) {
        return null
    }

    return (
        <Tag>
            <HorizontalStack gap={1}>
                <Avatar size="extraSmall" shape="round" source="/public/linear.svg" />
                <Link url={linearIssueUrl} target="_blank">
                    <Text as="span">{getLinearKey(linearIssueUrl)}</Text>
                </Link>
            </HorizontalStack>
        </Tag>
    )
}

export default LinearTicketDisplay
