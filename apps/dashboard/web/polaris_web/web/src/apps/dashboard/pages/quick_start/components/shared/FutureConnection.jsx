import { Banner, Text} from '@shopify/polaris'
import React from 'react'

function FutureConnection() {
    return (
        <div className='card-items'>
            <Banner status="info" title='Coming Soon'>
                <Text variant="headingSm" as="h4">
                    Sorry for the inconvenience, Akto is working on this connector.
                    Stay tuned!
                    <br/>
                    If you have an immediate usecase for this, please email us at help@akto.io
                </Text>
            </Banner>
        </div>
    )
}

export default FutureConnection