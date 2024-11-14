import { Avatar, Button, InlineStack, Text } from '@shopify/polaris'
import React from 'react'

function SSOTextfield({onClickFunc, logos , text}) {
    return (
        <Button onClick={onClickFunc}>
            <InlineStack align="center" gap={"3"}>
                <InlineStack gap={"2"}>
                    {logos.map((logo, index) => {
                        return <Avatar size="xs" source={logo} key={index}/>;
                    })}
                </InlineStack>
                <Text variant="bodyLg" fontWeight="semibold">{text}</Text>
            </InlineStack>
        </Button>
    );
}

export default SSOTextfield