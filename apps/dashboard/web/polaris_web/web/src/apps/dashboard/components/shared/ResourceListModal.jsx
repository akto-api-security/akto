import { Modal, Text } from '@shopify/polaris'
import React, { useState } from 'react'

function ResourceListModal({ isLarge, activatorPlaceaholder, isColoredActivator, title, titleHidden, primaryAction, component }) {
    const [popup, setPopup] = useState(false)
    const activatorText = isColoredActivator ? <Text color='subdued'>{activatorPlaceaholder}</Text> : activatorPlaceaholder

    return (
        <Modal
            large={isLarge}
            activator={<div onClick={() => setPopup(!popup)}>{activatorText}</div>}
            open={popup}
            onClose={() => setPopup(false)}
            title={title}
            titleHidden={titleHidden}
            primaryAction={{
                content: 'Save',
                onAction: () => {
                    const flag = primaryAction()
                    if(flag) setPopup(false)
                },
            }}
            secondaryActions={{
                content: 'Cancel',
                onAction: () => setPopup(false)
            }}
        >
            {component}
        </Modal>
    )
    }

export default ResourceListModal