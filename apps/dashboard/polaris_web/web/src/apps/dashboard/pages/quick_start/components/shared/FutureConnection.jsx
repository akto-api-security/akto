import { Banner, Text} from '@shopify/polaris'
import React from 'react'

function FutureConnection() {
    return (
        <div className='card-items'>
            <Banner status="info" title='Coming Soon'>
                <Text variant="headingSm" as="h4">
                    Sorry for the inconvenience, Akto is working on the feature.
                    Stay tune.
                </Text>
            </Banner>
        </div>
    )
}

export default FutureConnection