import { Box, Card, HorizontalStack, Link, VerticalStack } from '@shopify/polaris';
import React from 'react';
import TitleWithInfo from "@/apps/dashboard/components/shared/TitleWithInfo"

function InfoCard({component, title, titleToolTip, linkText, linkUrl}) {
    return (
        <Card padding={2} key="info">
            <Box padding={2} paddingInlineStart={4} paddingInlineEnd={4}>
                <VerticalStack gap={4}>
                    <HorizontalStack align='space-between'>
                        <TitleWithInfo
                            tooltipContent={titleToolTip}
                            titleText={title}
                        />
                        <Link url={linkUrl} target="_blank">{linkText}</Link>
                    </HorizontalStack>
                    {component}
                </VerticalStack>
            </Box>
        </Card>
    );
}

export default InfoCard;
