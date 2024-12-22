import { ActionList, Box, Button, Icon, Popover, Text, BlockStack } from '@shopify/polaris'
import { PlayIcon, NoteIcon } from "@shopify/polaris-icons";
import React, { useState } from 'react'
import '../../pages/observe/api_collections/apiCollections.css'

function LearnPopoverComponent({learnMoreObj}) {
    const [popoverActive,setPopoverActive] = useState(false)
    if(learnMoreObj){
        if (learnMoreObj?.docsLink !== undefined) {
            learnMoreObj.docsLink.forEach((doc) => {
                doc.prefix = <Box><Icon source={NoteIcon} /></Box>;
                doc.onAction = () => window.open(doc.value, "_blank")
            });
        }

        if (learnMoreObj?.videoLink !== undefined) {
            learnMoreObj.videoLink.forEach((doc) => {
                doc.prefix = <Box><Icon source={PlayIcon} /></Box>;
                doc.onAction = () => window.open(doc.value, "_blank")
            });
        }
    }
    return (
        <Popover
            active={popoverActive}
            activator={(
                <div className="polaris-secondaryAction-button">
                    <Button variant="secondary" onClick={() => setPopoverActive(!popoverActive)} disclosure>
                        Learn
                    </Button>
                </div>
            )}
            autofocusTarget="first-node"
            onClose={() => { setPopoverActive(false) }}
            preferredAlignment="right"
        >
            {(learnMoreObj?.title !==undefined ||  learnMoreObj.description !== undefined) ?
            <Box width="230px" padding={400} paddingBlockEnd={"0"}>
                <BlockStack gap={100}>
                    <Text>
                        {learnMoreObj?.title}
                    </Text>
                    <Text tone="subdued">
                        {learnMoreObj?.description}
                    </Text>
                </BlockStack>
            </Box> : null}
            <ActionList items={[...learnMoreObj?.docsLink, ...learnMoreObj?.videoLink]} />
            
        </Popover>
    );
}

export default LearnPopoverComponent