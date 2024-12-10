import { Banner, Button } from '@shopify/polaris'
import React from 'react'

function BannerComponent({title,docsUrl,content}) {

    const openLink = () => {
        window.open(docsUrl)
    }
    return (
        <div className='card-items'>
            <Banner title={title} tone='warning'>
                <span>{content}</span>
                <br/>
                <Button  onClick={() => openLink()} variant="plain">Go to docs</Button>
            </Banner>
        </div>
    );
}

export default BannerComponent