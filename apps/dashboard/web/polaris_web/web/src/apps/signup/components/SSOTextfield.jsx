import { Avatar, Button, HorizontalStack, Text } from '@shopify/polaris'
import React from 'react'

function SSOTextfield({onClickFunc, logos , text}) {
    return (
        <Button onClick={onClickFunc}>
            <HorizontalStack align="center" gap={"3"}>
                <HorizontalStack gap={"2"}>
                    {logos.map((logo, index) => {
                        return(
                            <Avatar size="extraSmall" source={logo} key={index}/>
                        )
                    })}
                </HorizontalStack>
                
                <Text variant="bodyLg" fontWeight="semibold">{text}</Text>
            </HorizontalStack>
        </Button>
    )
}

export default SSOTextfield