import React, { useState } from 'react'
import { Avatar, Box, Button, Card, Divider, HorizontalStack, Tag, Text, VerticalStack } from '@shopify/polaris'
import GridRows from '../../../components/shared/GridRows'
import FlyLayout from '../../../components/layouts/FlyLayout'
import TitleWithInfo from '@/apps/dashboard/components/shared/TitleWithInfo'
import RuntimeTokenField from './RuntimeTokenField'

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

function GuardrailsSetupGuide() {
    return (
        <div className='card-items'>
            <Text variant='bodyMd'>
                Deploy Akto Guardrails to start protecting your AI agents and endpoints in minutes.
            </Text>
            <RuntimeTokenField
                id="select-guardrails-expiry"
                expiryStepText="Select the expiry time of the jwt token."
                tokenStepText="Copy the token below and set it as the guardrails service authentication token in your configuration."
            />
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
