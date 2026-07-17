import { Box, Card, HorizontalStack, Link, VerticalStack } from '@shopify/polaris';
import TitleWithInfo from "@/apps/dashboard/components/shared/TitleWithInfo"

// Content is bounded (fixed chart height, capped table rows), so minHeight renders like height.
const TITLE_AREA_HEIGHT_PX = "52px";
const COMPONENT_AREA_HEIGHT_PX = "320px";

function InfoCard({component, title, titleToolTip, linkText, linkUrl, minHeight, onLinkClick, fillHeight}) {
    const titleRow = (
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
    );

    // fillHeight gives title/component their own fixed-height Box slots.
    if (fillHeight) {
        return (
            <Card padding={5} key="info">
                <Box minHeight={TITLE_AREA_HEIGHT_PX}>{titleRow}</Box>
                <Box minHeight={COMPONENT_AREA_HEIGHT_PX} position="relative">
                    {component}
                </Box>
            </Card>
        );
    }

    return (
        <Card padding={5} key="info">
            <Box minHeight={minHeight ? minHeight : "100%"}>
                <VerticalStack gap={"2"}>
                    <VerticalStack gap={5} align='center'>
                        {titleRow}
                        {component}
                    </VerticalStack>
                </VerticalStack>
            </Box>
        </Card>
    );
}

export default InfoCard;
