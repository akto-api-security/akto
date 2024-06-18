import { Avatar, Button, HorizontalStack, Text } from '@shopify/polaris'
import React from 'react'

function SSOTextfield({onClickFunc, logo , text}) {
    return (
        <AktoButton  onClick={onClickFunc}>
            <HorizontalStack align="center" gap={"3"}>
                <Avatar size="extraSmall" source={logo} />
                <Text variant="bodyLg" fontWeight="semibold">{text}</Text>
            </HorizontalStack>
        </AktoButton>
    )
}

export default SSOTextfield