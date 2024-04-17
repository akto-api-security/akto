import { Avatar, Button, HorizontalStack, Text } from '@shopify/polaris'
import React from 'react'

function SSOTextfield({onClickFunc, logo , text}) {
    return (
        <Button onClick={onClickFunc}>
            <HorizontalStack align="center" gap={"3"}>
                <Avatar size="extraSmall" source={logo} />
                <Text variant="bodyLg" fontWeight="semibold">{text}</Text>
            </HorizontalStack>
        </Button>
    )
}

export default SSOTextfield