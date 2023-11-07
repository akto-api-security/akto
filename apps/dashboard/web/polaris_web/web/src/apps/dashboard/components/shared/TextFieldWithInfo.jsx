import React from 'react'
import { HorizontalStack, Text , Tooltip, Icon, TextField } from "@shopify/polaris"
import { QuestionMarkMinor } from "@shopify/polaris-icons"

function TextFieldWithInfo({labelText,labelTooltip, value, placeholder, setValue}) {

    const label = (
        <HorizontalStack gap="2">
            <Text>{labelText}</Text>
            <Tooltip content={labelTooltip} dismissOnMouseOut width="wide">
                <Icon source={QuestionMarkMinor} color="base" />
            </Tooltip>
        </HorizontalStack>
    )

    return (
        <TextField label={label} value={value} placeholder={placeholder} onChange={setValue} />
    )
}

export default TextFieldWithInfo