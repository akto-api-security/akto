import React, { useState } from 'react'
import { LegacyCard, TextField, Form, HorizontalStack, VerticalStack, Tag, Text, Divider, Box } from '@shopify/polaris'
import func from "@/util/func"
import { validateIpInput } from './ipUtils'

function TitleComponent({ title, description }) {
    return (
        <Box paddingBlockEnd="4">
            <Text variant="headingMd">{title}</Text>
            <Box paddingBlockStart="2">
                <Text variant="bodyMd">{description}</Text>
            </Box>
        </Box>
    )
}

function UpdateIpsComponent({ onSubmit, title, labelText, description, ipsList, onRemove, type, onApply, showTitle = true, showCard = true }) {
    const [value, setValue] = useState('')
    
    const onFormSubmit = (ip) => {
        if (isError) {
            func.setToast(true, true, "Invalid ip address")
        } else {
            setValue('')
            onSubmit(ip)
        }
    }

    const isError = validateIpInput(value, type)
    
    const content = (
        <VerticalStack gap={"2"}>
            <Form onSubmit={() => onFormSubmit(value)}>
                <TextField 
                    onChange={setValue} 
                    value={value} 
                    label={<Text color="subdued" fontWeight="medium" variant="bodySm">{labelText}</Text>} 
                    {...isError ? { error: "Invalid address" } : {}} 
                    placeholder={type === "cidr" ? "192.168.1.0/24" : "192.168.1.0"}
                />
            </Form>
            <HorizontalStack gap={"2"}>
                {ipsList && ipsList.length > 0 && ipsList.map((ip, index) => {
                    return (
                        <Tag key={index} onRemove={() => onRemove(ip)}>
                            <Text>{ip}</Text>
                        </Tag>
                    )
                })}
            </HorizontalStack>
        </VerticalStack>
    )

    if (!showCard) {
        return content
    }

    return (
        <LegacyCard 
            title={showTitle ? <TitleComponent title={title} description={description} /> : null}
            actions={onApply ? [{ content: 'Apply', onAction: onApply }] : []}
        >
            {showTitle && <Divider />}
            <LegacyCard.Section>
                {content}
            </LegacyCard.Section>
        </LegacyCard>
    )
}

export default UpdateIpsComponent