import { Badge, Box, Button, ButtonGroup, HorizontalStack, Icon, Link, Text, VerticalStack } from '@shopify/polaris'
import React from 'react'

function MoreInfoComponent({ icon, label, text, isLink, linkUrl }) {
    return (
        <HorizontalStack gap={"1"} align="center">
            <Box><Icon color="subdued" source={icon} /></Box>
            {label !== undefined ? <Text color="subdued" fontWeight="medium" variant="bodyMd">{label}</Text> : null}
            {isLink ?
                <Link url={linkUrl}><Text color="subdued" variant="bodyMd">{text}</Text></Link> :
                <Text color="subdued" variant="bodyMd">{text}</Text>
            }
        </HorizontalStack>
    )
}

function FlyoutHeadingComponent({ itemDetails }) {
    return (

        <HorizontalStack gap={"2"} align="space-between" wrap={false}>
            <Box minWidth="0" style={{ flex: 1 }}>
                <VerticalStack gap={"1"}>
                    <HorizontalStack gap={"2"} align="start">
                        <Text variant="headingMd" breakWord>{itemDetails.title}</Text>
                        <Badge status={itemDetails.priority}>{itemDetails.priorityValue}</Badge>
                    </HorizontalStack>
                    <HorizontalStack gap="3" wrap align="start">
                        {itemDetails.moreInfo.map((item, index) => (
                            <React.Fragment key={index}>
                                <HorizontalStack gap="1" align="center">
                                    <Icon source={item.icon} color="subdued" />
                                    <Text color="subdued" variant="bodyMd">{item.text}</Text>
                                </HorizontalStack>

                                {index !== itemDetails.moreInfo.length - 1 && (
                                    <Box
                                        borderInlineStartWidth="1"
                                        borderColor="border-subdued"
                                        minHeight="16px"
                                        aria-hidden="true"
                                    />
                                )}
                            </React.Fragment>
                        ))}
                    </HorizontalStack>
                </VerticalStack>
            </Box>

            <Box style={{ flexShrink: 0 }}>
                <ButtonGroup spacing="tight">
                    {itemDetails?.secondaryActions.map((action, index) => {
                        return (
                            <Box key={index + 10}>
                                {action.iconComp()}
                            </Box>
                        )
                    })}
                    {itemDetails?.primaryActionComp ? itemDetails.primaryActionComp :
                        (itemDetails?.primaryActionText ?
                            <Button plain={itemDetails?.isPrimaryPlain} removeUnderline onClick={itemDetails?.primaryAction}>
                                {itemDetails?.primaryActionText}
                            </Button>
                            : null
                        )
                    }
                </ButtonGroup>
            </Box>
        </HorizontalStack>

    )
}

export default FlyoutHeadingComponent