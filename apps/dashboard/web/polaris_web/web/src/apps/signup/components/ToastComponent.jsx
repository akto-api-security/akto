import React from 'react'
import Store from '../../dashboard/store'
import { Toast } from '@shopify/polaris'

function ToastComponent() {

    const toastConfig = Store(state => state.toastConfig)
    const setToastConfig = Store(state => state.setToastConfig)

    const disableToast = () => {
        setToastConfig({
        isActive: false,
        isError: false,
        message: ""
        })
    }

  const toastMarkup = toastConfig.isActive ? (
    <Toast content={toastConfig.message} error={toastConfig.isError} onDismiss={disableToast} duration={2000} />
  ) : null;
    return (
        toastMarkup
    )
}

export default ToastComponent