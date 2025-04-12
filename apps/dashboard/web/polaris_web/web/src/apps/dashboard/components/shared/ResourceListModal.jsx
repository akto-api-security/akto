import { Modal, Text } from '@shopify/polaris'
import React, { useState } from 'react'

function ResourceListModal({ isLarge, activatorPlaceaholder, isColoredActivator, title, titleHidden, primaryAction, component, secondaryAction, showDeleteAction, deleteAction }) {
    const [popup, setPopup] = useState(false)
    const activatorText = isColoredActivator ? <Text color='subdued'>{activatorPlaceaholder}</Text> : activatorPlaceaholder

    if(!secondaryAction) {
        secondaryAction = () => {}
    }

    let secondaryActions = []
    if (showDeleteAction) {
        secondaryActions.push({
            content: 'Delete',
            onAction: () => {
                deleteAction()
                setPopup(false)
            }
        })
    }
    secondaryActions.push({
        content: 'Cancel',
        onAction: () => {
            secondaryAction()
            setPopup(false)
        }
    })

    return (
        <Modal
            large={isLarge}
            activator={<div onClick={() => setPopup(!popup)}>{activatorText}</div>}
            open={popup}
            onClose={() => { 
            secondaryAction()
            setPopup(false)
             }}
            title={title}
            titleHidden={titleHidden}
            primaryAction={{
                content: 'Save',
                onAction: () => {
                    const flag = primaryAction()
                    if(flag) setPopup(false)
                },
            }}
            secondaryActions={secondaryActions}
        >
            {component}
        </Modal>
    )
    }

export default ResourceListModal