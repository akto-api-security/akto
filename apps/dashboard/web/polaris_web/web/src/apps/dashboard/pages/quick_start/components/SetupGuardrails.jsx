import React, { useEffect, useRef, useState } from 'react'
import { Avatar, Box, Button, Card, Divider, HorizontalStack, Tag, Text, Tooltip, VerticalStack } from '@shopify/polaris'
import { ClipboardMinor } from '@shopify/polaris-icons'
import api from '../api'
import func from "@/util/func"
import Dropdown from '../../../components/layouts/Dropdown'
import GridRows from '../../../components/shared/GridRows'
import FlyLayout from '../../../components/layouts/FlyLayout'
import TitleWithInfo from '@/apps/dashboard/components/shared/TitleWithInfo'

const guardrailsCard = {
    label: "Setup Guardrails",
    key: "setup_guardrails",
    icon: "/public/akto.svg",
    text: "Deploy Akto Guardrails in your cloud to protect your AI agents and endpoints."
}

function GuardrailsRowCard({ cardObj, buttonText, onButtonClick }) {
    return (
        <Card>
            <VerticalStack gap="5">
                <Box padding={"2"} borderWidth='1' borderColor='border-subdued' borderRadius='2' width='fit-content'>
                    <Avatar customer size="extraSmall" name={cardObj.label} source={cardObj.icon} shape='square' />
                </Box>
                <VerticalStack gap="1">
                    <Text variant="headingMd" as="h5">{cardObj.label}</Text>
                    <Box minHeight="80px">
                        <Text variant="bodyMd" color='subdued'>{cardObj.text}</Text>
                    </Box>
                </VerticalStack>
                <HorizontalStack gap={"4"} align='start'>
                    <Button onClick={() => onButtonClick(cardObj)}>{buttonText}</Button>
                </HorizontalStack>
            </VerticalStack>
        </Card>
    )
}

const expiryDurationOptions = [
    { label: '1 month', value: 1 },
    { label: '3 months', value: 3 },
    { label: '6 months', value: 6 },
    { label: '9 months', value: 9 },
    { label: '12 months', value: 12 },
    { label: 'Never expire', value: -1 }
]

function GuardrailsSetupGuide() {
    const [apiToken, setApiToken] = useState("")
    const [selectedExpiryDuration, setSelectedExpiryDuration] = useState(6)
    const ref = useRef(null)

    const copyCommandUtil = (data) => { func.copyToClipboard(data, ref, null) }

    const fetchGuardrailsToken = async (expiryDuration) => {
        await api.fetchRuntimeHelmCommand(expiryDuration).then((resp) => {
            if (!resp) return
            setApiToken(resp?.apiToken)
        })
    }

    const getLabelFromValue = (value) => {
        const option = expiryDurationOptions.find(option => option.value === value)
        return option ? option.label : ''
    }

    useEffect(() => {
        fetchGuardrailsToken(selectedExpiryDuration)
    }, [])

    return (
        <div className='card-items'>
            <Text variant='bodyMd'>
                Deploy Akto Guardrails to start protecting your AI agents and endpoints in minutes.
            </Text>
            <VerticalStack gap="2">
                <div ref={ref} />

                <span>1. Select the expiry time of the jwt token. </span>
                <Box maxWidth="180px" paddingInlineStart={"4"}>
                    <Dropdown
                        id={`select-guardrails-expiry`}
                        menuItems={expiryDurationOptions}
                        value={getLabelFromValue(selectedExpiryDuration)}
                        initial={selectedExpiryDuration}
                        selected={(type) => { setSelectedExpiryDuration(type); fetchGuardrailsToken(type) }}
                    />
                </Box>

                <span>2. Copy the token below and set it as the guardrails service authentication token in your configuration. </span>
                <Box paddingInlineStart={"4"}>
                    <HorizontalStack gap="2" blockAlign="center" wrap={false}>
                        <Box background="bg-subdued" padding="2" borderRadius="2" borderWidth="1" borderColor="border-subdued">
                            <Text variant="bodyMd" breakWord>{apiToken}</Text>
                        </Box>
                        <Tooltip content="Copy token">
                            <Button icon={ClipboardMinor} plain onClick={() => copyCommandUtil(apiToken)} />
                        </Tooltip>
                    </HorizontalStack>
                </Box>
            </VerticalStack>
        </div>
    )
}

function SetupGuardrails() {
    const [show, setShow] = useState(false)

    return (
        <>
            <VerticalStack gap="4">
                <HorizontalStack gap="3">
                    <Text variant="headingMd" as="h6">Setup Guardrails</Text>
                    <Tag>1</Tag>
                </HorizontalStack>
                <Divider />
                <GridRows
                    CardComponent={GuardrailsRowCard}
                    columns="3"
                    items={[guardrailsCard]}
                    buttonText="Connect"
                    onButtonClick={() => setShow(true)}
                />
            </VerticalStack>
            <FlyLayout
                width={"27vw"}
                titleComp={
                    <TitleWithInfo
                        tooltipContent={"Deploy Akto guardrails in your environment"}
                        titleText={"Setup Guardrails"}
                    />
                }
                show={show}
                components={[<GuardrailsSetupGuide key="guardrails-setup-guide" />]}
                isHandleClose={true}
                handleClose={() => setShow(false)}
                setShow={setShow}
            />
        </>
    )
}

export default SetupGuardrails
