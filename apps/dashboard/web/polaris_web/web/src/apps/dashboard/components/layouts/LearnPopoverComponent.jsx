import { ActionList, Box, Button, Icon, Popover, Text, VerticalStack } from '@shopify/polaris'
import { PlayMinor, NoteMinor } from "@shopify/polaris-icons"
import React, { useState } from 'react'

function LearnPopoverComponent({learnMoreObj}) {
    const [popoverActive,setPopoverActive] = useState(false)

    // Prepare items without mutating original data
    const docItems = (learnMoreObj?.docsLink || []).map((doc) => ({
        content: doc.content,
        value: doc.value,
        prefix: <Box><Icon source={NoteMinor} /></Box>,
        onAction: () => window.open(doc.value, "_blank")
    }));

    const videoItems = (learnMoreObj?.videoLink || []).map((doc) => ({
        content: doc.content,
        value: doc.value,
        prefix: <Box><Icon source={PlayMinor} /></Box>,
        onAction: () => window.open(doc.value, "_blank")
    }));

    const allItems = [...docItems, ...videoItems];

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
            <ActionList items={allItems} />
            
        </Popover>
    )
}

export default LearnPopoverComponent