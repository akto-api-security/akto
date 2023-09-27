import { Banner, Button } from '@shopify/polaris'
import React from 'react'

function BannerComponent({title,docsUrl,content}) {

    const openLink = () => {
        window.open(docsUrl)
    }
    return (
        <div className='card-items'>
            <Banner title={title} status='warning'>
                <span>{content}</span>
                <br/>
                <Button plain onClick={() => openLink()}>Go to docs</Button>
            </Banner>
        </div>
    )
}

export default BannerComponent