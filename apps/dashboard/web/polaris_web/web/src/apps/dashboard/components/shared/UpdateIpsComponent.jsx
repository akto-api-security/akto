import React, { useState } from 'react'
import { LegacyCard, TextField, Form, HorizontalStack, VerticalStack, Tag, Text, Divider, Box, Button } from '@shopify/polaris'
import { isIP } from "is-ip"
import isCidr from "is-cidr"
import func from "@/util/func"

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
        if (checkError(ip, type)) {
            func.setToast(true, true, "Invalid ip address")
        } else {
            setValue('')
            onSubmit(ip)
        }
    }

    const checkError = (localVal, localType) => {
        localVal = localVal.replace(/\s+/g, '')
        if (localVal.length === 0) return false
        const values = localVal.split(",")
        let valid = true;
        for (let v of values) {
            if (v.length === 0) {
                return true
            }
            if (localType === "cidr") {
                valid = valid && (isCidr(v) !== 0)
            } else if (localType === "ip") {
                valid = valid && (isIP(v))
            } else {
                valid = valid && (isIP(v))
            }
        }

        return !valid
    }

    const isError = checkError(value, type)
    
    const content = (
        <VerticalStack gap={"2"}>
            <Form onSubmit={() => onFormSubmit(value)}>
                <HorizontalStack gap={"2"} align="end">
                    <div style={{ flexGrow: 1 }}>
                        <TextField 
                            onChange={setValue} 
                            value={value} 
                            label={<Text color="subdued" fontWeight="medium" variant="bodySm">{labelText}</Text>} 
                            {...isError ? { error: "Invalid address" } : {}} 
                            placeholder={type === "cidr" ? "192.168.1.0/24" : "192.168.1.0"}
                        />
                    </div>
                    <div style={{ paddingTop: '6px' }}>
                        <Button 
                            submit 
                            primary 
                            disabled={!value || value.trim().length === 0 || isError}
                        >
                            Add
                        </Button>
                    </div>
                </HorizontalStack>
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