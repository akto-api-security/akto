import { Banner, Button } from '@shopify/polaris'
import React from 'react'

function InformationBannerComponent({docsUrl,content}) {

    const openLink = () => {
        window.open(docsUrl)
    }
    return (
        <div className='card-items'>
            <Banner onDismiss={()=>{}}>
                <p style={{color:"var(--p-color-bg-inverse)"}}>{content}</p>
                {docsUrl !== '' ?  <Button  onClick={() => openLink()} variant="plain">here</Button>: ""}
            </Banner>
        </div>
    );
}

export default InformationBannerComponent