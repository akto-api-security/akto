import { Banner, Button } from '@shopify/polaris'
import React from 'react'

function InformationBannerComponent({docsUrl,content}) {

    const openLink = () => {
        window.open(docsUrl)
    }
    return (
        <div className='card-items'>
            <Banner status="info">
                <span>{content}</span>
                {docsUrl !== '' ?  <Button plain onClick={() => openLink()}>here</Button>: ""}
            </Banner>
        </div>
    )
}

export default InformationBannerComponent