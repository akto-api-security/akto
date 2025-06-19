import { Badge, Box, Button, ButtonGroup, HorizontalStack, Icon, Link, Text, VerticalStack } from '@shopify/polaris'
import React from 'react'

function MoreInfoComponent({icon, label, text, isLink, linkUrl}){
    return (
        <HorizontalStack gap={"1"}>
            <Box><Icon color="subdued" source={icon} /></Box>
            {label !== undefined ? <Text color="subdued" fontWeight="medium" variant="bodyMd">{label}</Text> : null}
            {isLink ? <Link url={linkUrl}><Text color="subdued" variant="bodyMd">{text}</Text> </Link> : <Text color="subdued" variant="bodyMd">{text}</Text>}
        </HorizontalStack>
    )
}

function FlyoutHeadingComponent({itemDetails}) {
    return (
        <HorizontalStack gap={"2"} align="space-between" wrap={true}>
           
            <Box maxWidth="350px">
                <VerticalStack gap={"2"}>
                    <HorizontalStack gap={"2"}>
                        <Text variant="headingMd">{itemDetails.title}</Text>
                        <Badge status={itemDetails.priority}>{itemDetails.priorityValue}</Badge>
                    </HorizontalStack>
                    <HorizontalStack gap={"2"} wrap={true}>
                        {
                            itemDetails.moreInfo.map((item, index) => {
                                return (
                                    <HorizontalStack key={index} gap={"3"}>
                                        <MoreInfoComponent {...item} />
                                        {index !== itemDetails.moreInfo.length - 1 ? <Box width="1px" borderColor="border-subdued" borderInlineStartWidth="1" minHeight='16px' /> : null}
                                    </HorizontalStack>
                                )
                            })  
                        }
                    </HorizontalStack>
                    </VerticalStack>
            </Box>
            
            <ButtonGroup spacing="tight">
                {itemDetails?.secondaryActions.map((action, index) => {
                    return (
                        <Button 
                            key={index + 10} 
                            icon={action.iconComp}
                            onClick={action.onClick} 
                        />
                    )
                })}
            {itemDetails?.primaryActionComp ? itemDetails?.primaryActionComp : <Button plain={itemDetails?.isPrimaryPlain} removeUnderline onClick={itemDetails?.primaryAction}>{itemDetails?.primaryActionText}</Button>}
            </ButtonGroup>
        </HorizontalStack>  
    )
}

export default FlyoutHeadingComponent