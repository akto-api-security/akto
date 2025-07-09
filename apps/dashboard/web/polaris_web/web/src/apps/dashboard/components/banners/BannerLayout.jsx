import React from 'react'
import { Box, Button, Card, Text, VerticalStack, VideoThumbnail } from "@shopify/polaris"
import { useNavigate } from 'react-router-dom';

function BannerLayout({title, text, buttonText, buttonUrl, bodyComponent, videoThumbnail, videoLink, videoLength, linkButton, containerComp, newTab, onClick, disabled=false}) {
    const properties = linkButton ? {plain: true} : {primary: true}
    const navigate = useNavigate();
    const handleRedirect = () =>{ newTab ? window.open(buttonUrl, "_blank") :navigate(buttonUrl)}
    return (
        <Card padding={"10"}>
            <VerticalStack gap={8}>
                <div style={{display: "flex", justifyContent: 'space-between'}}>
                    <Box width="400px">
                        <VerticalStack gap={4}>
                            <Text variant="headingLg">{title}</Text>
                            <Text color="subdued" variant="bodyMd">{text}</Text>
                            {bodyComponent}
                            {buttonText ? <Box paddingBlockStart={2}>
                                <Button {...properties} disabled={disabled} onClick={() => {handleRedirect(); onClick()}}>{buttonText}</Button>
                            </Box> : null}
                        </VerticalStack>
                    </Box>
                    {videoLink ? <Box width='340px'>
                        <VideoThumbnail
                            videoLength={videoLength}
                            thumbnailUrl={videoThumbnail}
                            onClick={() => window.open(videoLink, "_blank")}
                        />
                    </Box> : null}
                </div>
                {containerComp}
            </VerticalStack>
        </Card>
    )
}

export default BannerLayout