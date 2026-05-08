import { Box, Card, HorizontalStack, Link, VerticalStack } from '@shopify/polaris';
import TitleWithInfo from "@/apps/dashboard/components/shared/TitleWithInfo"

function InfoCard({component, title, titleToolTip, linkText, linkUrl, minHeight, onLinkClick}) {
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
                            {linkText && (
                                onLinkClick ? (
                                    <Link onClick={onLinkClick} removeUnderline>{linkText}</Link>
                                ) : (
                                    <Link url={linkUrl} target="_blank">{linkText}</Link>
                                )
                            )}
                        </HorizontalStack>
                        {component}
                    </VerticalStack>
                </VerticalStack>
            </Box>
        </Card>
    );
}

export default InfoCard;
