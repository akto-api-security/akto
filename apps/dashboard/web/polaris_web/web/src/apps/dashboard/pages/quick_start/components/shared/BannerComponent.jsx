import AktoButton from './../../../../components/shared/AktoButton';
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
                <AktoButton  plain onClick={() => openLink()}>Go to docs</AktoButton>
            </Banner>
        </div>
    )
}

export default BannerComponent