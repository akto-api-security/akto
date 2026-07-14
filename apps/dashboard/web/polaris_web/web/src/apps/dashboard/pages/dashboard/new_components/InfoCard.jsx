import { Box, Card, HorizontalStack, Link, VerticalStack } from '@shopify/polaris';
import TitleWithInfo from "@/apps/dashboard/components/shared/TitleWithInfo"

// Literal px, not "100%": Box is a plain block, so a % height on its child never resolves.
const FILL_HEIGHT_PX = "350px";

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

    // fillHeight skips Box (a plain block) and sets height directly on a flex div instead.
    if (fillHeight) {
        return (
            <Card padding={5} key="info">
                <div style={{ height: FILL_HEIGHT_PX, display: "flex", flexDirection: "column", gap: 20 }}>
                    {titleRow}
                    <div style={{ flex: 1, minHeight: 0, display: "grid" }}>{component}</div>
                </div>
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
