import React from 'react'
import { Avatar, HorizontalStack, Text } from "@shopify/polaris"
import BannerLayout from '../../../../components/banners/BannerLayout'

function CollectionsPageBanner() {

    const iconsList=[
        '/public/burp.svg',
        '/public/aws.svg',
        '/public/gcp.svg',
        '/public/postman.svg'
    ]
    
    const iconComponent = (
        <HorizontalStack gap={"2"} align="start">
            <HorizontalStack>
                {iconsList.map((iconUrl, index) => {
                    return(
                        <div style={{display: "flex", justifyContent: 'center', alignItems: 'center', background: "var(--white)",
                                border: "1px solid #ECECEC", width: '40px', height: '40px' , borderRadius: '32px',
                                boxShadow: "0rem 0.125rem 0.25rem rgba(31, 33, 36, 0.1), 0rem 0.0625rem 0.375rem rgba(31, 33, 36, 0.05)",
                                ...(index > 0 ? {marginLeft: '-10px'} : {})}} key={iconUrl}
                        >
                            <Avatar shape="square" source={iconUrl} size="extraSmall" />
                        </div>
                    )
                })}
            </HorizontalStack>
            <Text color="subdued" variant="bodyMd">+11 more</Text>
        </HorizontalStack>
    )

    return(
        <BannerLayout 
            title={"Upload your traffic to get started"}
            text={"Lorem ipsum dolor sit amet, consectetur adipiscing elit. Curabitur dignissim lacinia maximus."}
            buttonText={"Go to quickstart"}
            buttonUrl={"/dashboard/quick-start"}
            bodyComponent={iconComponent}
        />
    )
    
}

export default CollectionsPageBanner