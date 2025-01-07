import { ActionList, Box, Button, Icon, Popover, Text, VerticalStack } from '@shopify/polaris'
import { PlayMinor, NoteMinor } from "@shopify/polaris-icons"
import React, { useState } from 'react'

function LearnPopoverComponent({learnMoreObj}) {
    const [popoverActive,setPopoverActive] = useState(false)
    if(learnMoreObj){
        if (learnMoreObj?.docsLink !== undefined) {
            learnMoreObj.docsLink.forEach((doc) => {
                doc.prefix = <Box><Icon source={NoteMinor} /></Box>;
                doc.onAction = () => window.open(doc.value, "_blank")
            });
        }

        if (learnMoreObj?.videoLink !== undefined) {
            learnMoreObj.videoLink.forEach((doc) => {
                doc.prefix = <Box><Icon source={PlayMinor} /></Box>;
                doc.onAction = () => window.open(doc.value, "_blank")
            });
        }
    }

    return (
        <Popover
            active={popoverActive}
            activator={(
                <Button onClick={() => setPopoverActive(!popoverActive)} disclosure>
                    Learn
                </Button>
            )}
            autofocusTarget="first-node"
            onClose={() => { setPopoverActive(false) }}
            preferredAlignment="right"
        >
            {(learnMoreObj?.title !==undefined ||  learnMoreObj.description !== undefined) ?
            <Box width="230px" padding={4} paddingBlockEnd={"0"}>
                <VerticalStack gap={1}>
                    <Text>
                        {learnMoreObj?.title}
                    </Text>
                    <Text color="subdued">
                        {learnMoreObj?.description}
                    </Text>
                </VerticalStack>
            </Box> : null}
            <ActionList items={[...learnMoreObj?.docsLink, ...learnMoreObj?.videoLink || []]} />
            
        </Popover>
    )
}

export default LearnPopoverComponent