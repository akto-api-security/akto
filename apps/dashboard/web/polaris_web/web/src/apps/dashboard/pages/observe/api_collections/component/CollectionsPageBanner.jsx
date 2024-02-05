import React from 'react'
import { Avatar, HorizontalStack, Text } from "@shopify/polaris"
import BannerLayout from '../../../../components/banners/BannerLayout'
import { COLLECTIONS_VIDEO_LENGTH, COLLECTIONS_VIDEO_THUMBNAIL, COLLECTIONS_VIDEO_URL } from '../../../../../main/onboardingData'

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
                                width: '40px', height: '40px' , borderRadius: '32px', zIndex: `${index + 5}`,
                                boxShadow: "0px 2px 16px 0px rgba(33, 43, 54, 0.08), 0px 0px 0px 1px rgba(6, 44, 82, 0.10)",
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
            text={"Akto Discovers API inventory by connecting to your source of traffic. Connect a traffic connector to get started."}
            buttonText={"Go to quickstart"}
            buttonUrl={"/dashboard/quick-start"}
            bodyComponent={iconComponent}
            videoLength={COLLECTIONS_VIDEO_LENGTH}
            // videoLink={COLLECTIONS_VIDEO_URL}
            videoThumbnail={COLLECTIONS_VIDEO_THUMBNAIL}
        />
    )
    
}

export default CollectionsPageBanner