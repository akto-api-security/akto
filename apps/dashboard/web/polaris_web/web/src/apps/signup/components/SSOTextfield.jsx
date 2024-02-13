import { Avatar, Box, HorizontalStack, Text } from '@shopify/polaris'
import React from 'react'

function SSOTextfield({onClickFunc, logo , text}) {
    return (
        <div style={{cursor: "pointer"}} onClick={() => onClickFunc()}>
            <Box borderRadius="1" paddingBlockEnd={3} paddingBlockStart={3} 
                paddingInlineEnd={6} paddingInlineStart={6}
                shadow="border-inset-experimental"
            >
                <HorizontalStack align="center" gap={"3"}>
                    <Avatar source={logo} />
                    <Text variant="bodyLg" fontWeight="semibold">{text}</Text>
                </HorizontalStack>
            </Box>
        </div>
    )
}

export default SSOTextfield