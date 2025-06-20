import { Box, Card, HorizontalStack, Link, Text, VerticalStack } from '@shopify/polaris';
import TitleWithInfo from "@/apps/dashboard/components/shared/TitleWithInfo"

function InfoCard({component, title, titleToolTip, linkText, linkUrl, minHeight, totalAPIs, comparisonValue}) {
    return (
        <Card padding={5} key="info">
            
            <Box minHeight={minHeight ? minHeight : "100%"}>
                <VerticalStack gap={"2"}>
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
                    {totalAPIs && comparisonValue && comparisonValue < totalAPIs ? <Text variant="bodySm" color="subdued">Note: Need more information for calculation of {title.toLowerCase()}</Text> : null}
                </VerticalStack>
            </Box>
        </Card>
    );
}

export default InfoCard;
