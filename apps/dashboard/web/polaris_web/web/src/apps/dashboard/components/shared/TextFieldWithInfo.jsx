import React from 'react'
import { InlineStack, Text , Tooltip, Icon, TextField } from "@shopify/polaris"
import { QuestionCircleIcon } from "@shopify/polaris-icons";

function TextFieldWithInfo({labelText, labelTextColor, labelTooltip, tooltipIconColor, value, placeholder, setValue }) {

    const labelTextColorV = labelTextColor || ""
    const tooltipIconColorV = tooltipIconColor || "base"

    const label = (
        <InlineStack gap="200">
            <Text color={labelTextColorV}>{labelText}</Text>
            <Tooltip content={labelTooltip} dismissOnMouseOut width="wide">
                <Icon source={QuestionCircleIcon} tone={tooltipIconColorV} />
            </Tooltip>
        </InlineStack>
    )

    return (
        <TextField label={label} value={value} placeholder={placeholder} onChange={setValue} />
    )
}

export default TextFieldWithInfo