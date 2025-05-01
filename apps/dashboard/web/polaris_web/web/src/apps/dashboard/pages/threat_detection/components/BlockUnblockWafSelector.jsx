import { Avatar, Box, HorizontalStack, Modal, Text, VerticalStack } from '@shopify/polaris'
import React from 'react'

const BlockUnblockWafSelector = ({active, setActive, handleBlockUnblockIP}) => {
    return (
        <Modal
            open={active}
            onClose={() => setActive(false)}
            size="small"
            title="Select WAF"
            secondaryActions={[
                {
                    content: 'Cancel',
                    onAction: () => {
                        setActive(false)
                    },
                },
            ]}
        >
            <Modal.Section>
                <Box>
                    <HorizontalStack align='space-around' padding={"4"} paddingBlockStart={"0"} paddingInlineStart={"0"} paddingInlineEnd={"0"}>
                        {
                            window.IS_AWS_WAF_INTEGRATED === "true" &&
                            <div style={{cursor: 'pointer' }} onClick={() => {
                                handleBlockUnblockIP("aws")
                                setActive(false)
                            }}>
                                <VerticalStack inlineAlign='center' gap={"2"}>
                                    <Avatar customer size="medium" name={"AWS WAF"} source={"/public/awsWaf.svg"}/>
                                    <Text fontWeight="bold" as="h3">AWS WAF</Text>
                                </VerticalStack>
                            </div>
                        }

                        {
                            window.IS_CLOUDFLARE_WAF_INTEGRATED === "true" &&
                            <div style={{cursor: 'pointer'}} onClick={() => {
                                handleBlockUnblockIP("cloudflare")
                                setActive(false)
                            }}>
                                <VerticalStack inlineAlign='center' gap={"2"}>
                                    <Avatar customer size="medium" name={"Cloudflare WAF"} source={"/public/cloudflareWaf.png"}/>
                                    <Text fontWeight="bold" as="h3">Cloudflare WAF</Text>
                                </VerticalStack>
                            </div>
                        }
                    </HorizontalStack>
                </Box>
            </Modal.Section>
        </Modal>
    )
}

export default BlockUnblockWafSelector