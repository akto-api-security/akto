import React from 'react'
import { Frame, ContextualSaveBar } from '@shopify/polaris'
import "./ContextualLayoutStyle.css"

function ContextualLayout(props){

    const {saveAction, discardAction, isDisabled, pageMarkup } = props

    const logo = {
        width: 124,
        contextualSaveBarSource:'/public/logo.svg',
        url: '#',
        accessibilityLabel: 'Akto Icon',
      };
    const disabledActive = isDisabled()
    const contextualMarkup = (
    <ContextualSaveBar
            message="Unsaved changes"
            saveAction={{
            onAction: () => saveAction(),
            loading: false,
            disabled: disabledActive,
            content: "Save"
            }}
            discardAction={{
            onAction: () => discardAction(),
            content: "Discard",
            disabled: disabledActive,
            }}
        />
    )

    return (
        <div className='control-frame-padding'>
          <Frame logo={logo}>
            {!disabledActive ? contextualMarkup : null}
            {pageMarkup}
          </Frame>
        </div>
      )
}

export default ContextualLayout