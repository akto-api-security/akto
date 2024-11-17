import { Box, Card, InlineStack, Link, BlockStack } from '@shopify/polaris';
import React from 'react';
import TitleWithInfo from "@/apps/dashboard/components/shared/TitleWithInfo"

function InfoCard({component, title, titleToolTip, linkText, linkUrl, minHeight}) {
    return (
        <Card padding={500} key="info">
            <Box  minHeight={minHeight ? minHeight : "100%"}>
                <BlockStack gap={500} align='center'>
                    <InlineStack align='space-between'>
                        <TitleWithInfo
                            tooltipContent={titleToolTip}
                            titleText={title}
                            textProps={{variant: 'headingMd'}}
                        />
                        <Link url={linkUrl} target="_blank">{linkText}</Link>
                    </InlineStack>
                    {component}
                </BlockStack>
            </Box>
        </Card>
    );
}

export default InfoCard;
