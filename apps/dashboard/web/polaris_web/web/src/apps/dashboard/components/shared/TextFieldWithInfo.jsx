import React from 'react'
import { HorizontalStack, Text , Tooltip, Icon, TextField } from "@shopify/polaris"
import { QuestionMarkMinor } from "@shopify/polaris-icons"

function TextFieldWithInfo({labelText, labelTextColor, labelTooltip, tooltipIconColor, value, placeholder, setValue }) {

    const labelTextColorV = labelTextColor || ""
    const tooltipIconColorV = tooltipIconColor || "base"

    const label = (
        <HorizontalStack gap="2">
            <Text color={labelTextColorV}>{labelText}</Text>
            <Tooltip content={labelTooltip} dismissOnMouseOut width="wide">
                <Icon source={QuestionMarkMinor} color={tooltipIconColorV} />
            </Tooltip>
        </HorizontalStack>
    )

    return (
        <TextField label={label} value={value} placeholder={placeholder} onChange={setValue} />
    )
}

export default TextFieldWithInfo