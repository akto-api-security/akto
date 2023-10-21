import React, { useCallback, useState } from 'react'
import HomePage from "../../pages/home/HomePage"
import { Frame, Toast } from '@shopify/polaris'

function ErrorComponent() {

    const [active, setActive] = useState(true)

    const toggleActive = useCallback(() => setActive((active) => !active), []);

    return (
        <Frame>
            <HomePage />
            {active ? <Toast content="Some error occured !!!" error onDismiss={toggleActive} /> : null}
        </Frame>
    )
}

export default ErrorComponent