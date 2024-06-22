import React from 'react'
import { Avatar, HorizontalStack, Text } from "@shopify/polaris"
import BannerLayout from '../../../../components/banners/BannerLayout'
import { COLLECTIONS_VIDEO_LENGTH, COLLECTIONS_VIDEO_THUMBNAIL, COLLECTIONS_VIDEO_URL } from '../../../../../main/onboardingData'
import quickStartFunc from "../../../quick_start/transform"
import "../api_inventory.css"

function CollectionsPageBanner() {

    const iconsList=[
        '/public/burp.svg',
        '/public/aws.svg',
        '/public/gcp.svg',
        '/public/postman.svg'
    ]

    const connectorsList = quickStartFunc.getConnectorsList()
    
    const iconComponent = (
        <HorizontalStack gap={"2"} align="start">
            <HorizontalStack>
                {iconsList.map((iconUrl, index) => {
                    return(
                        <div className="icons-style" style={{zIndex: `${index + 5}`,...(index > 0 ? {marginLeft: '-10px'} : {})}} key={iconUrl}>
                            <Avatar shape="square" source={iconUrl} size="extraSmall" />
                        </div>
                    )
                })}
            </HorizontalStack>
            <Text color="subdued" variant="bodyMd">{`+${Math.max(connectorsList.length - 4, 18)} more`}</Text>
        </HorizontalStack>
    )

    return(
        <BannerLayout 
            title={"Upload your traffic to get started"}
            text={"Akto Discovers API inventory by connecting to your source of traffic. Connect a traffic connector to get started."}
            buttonText={"Go to Quickstart"}
            buttonUrl={"/dashboard/quick-start"}
            bodyComponent={iconComponent}
            videoLength={COLLECTIONS_VIDEO_LENGTH}
            // videoLink={COLLECTIONS_VIDEO_URL}
            videoThumbnail={COLLECTIONS_VIDEO_THUMBNAIL}
        />
    )
    
}

export default CollectionsPageBanner
