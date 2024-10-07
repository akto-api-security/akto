import { Box, Card, HorizontalStack, Link, VerticalStack } from '@shopify/polaris';
import React from 'react';
import TitleWithInfo from "@/apps/dashboard/components/shared/TitleWithInfo"

function InfoCard({component, title, titleToolTip, linkText, linkUrl, minHeight}) {
    return (
        <Card padding={5} key="info">
            <Box  minHeight={minHeight ? minHeight : "100%"}>
                <VerticalStack gap={5} align='center'>
                    <HorizontalStack align='space-between'>
                        <TitleWithInfo
                            tooltipContent={titleToolTip}
                            titleText={title}
                            textProps={{variant: 'headingMd'}}
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
