import {  Text } from '@shopify/polaris'


function BrowserExtension({browserName}) {
    return (
        <div className='card-items'>
            <Text variant='bodyMd'>
                Akto Team will provide Extension , Install in your {browserName} .
            </Text>
        </div>
    )
}

export default BrowserExtension