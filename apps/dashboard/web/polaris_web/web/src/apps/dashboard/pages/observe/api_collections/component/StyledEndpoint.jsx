import { Tooltip, Text, Box } from "@shopify/polaris"
import func from "@/util/func"
import "../api_inventory.css"
import { useRef, useEffect, useState } from "react"
import transform from "../../transform"
import { getMethod } from "../../GetPrettifyEndpoint"
import onboardingTransform from "../../../onboarding/transform"

const StyledEndpoint = (data, fontSize, variant, shouldNotTruncate, showWithoutHost) => {
    const { method, url } = func.toMethodUrlObject(data)
    let finalMethod = getMethod(url, method);
    let absoluteUrl = showWithoutHost ? transform.getTruncatedUrl(url) : url
    const arr = absoluteUrl.split("/")
    let colored = []
    arr.forEach((item, index) => {
        if (item.startsWith("{param")) {
            colored.push(index);
        }
    })

    let finalFontSize = fontSize ? fontSize: "16px"
    let finalVariant = variant ? variant : "headingMd"

    const ref = useRef(null);
    const [isTruncated, setIsTruncated] = useState(false);

    useEffect(() => {
        const element = ref.current;
        if(!shouldNotTruncate){
            setIsTruncated(element.scrollWidth > element.clientWidth);
        }
    }, []);

    const endpoint = (
        <div style={{display: 'flex', gap: "8px"}}>
            <div style={{color: onboardingTransform.getTextColor(finalMethod), fontSize: finalFontSize, fontWeight: 600}}>
                {finalMethod}
            </div>
            <div className={shouldNotTruncate ? "full-url" : "styled-endpoint"} ref={ref}>
                {
                    arr?.map((item, index) => {
                        return (
                            <Box key={index} as={"span"} color={colored.includes(index) ? "text-critical-active" : ""}>
                                <Text as="span" variant={finalVariant}>
                                {item + "/"}
                            </Text>
                            </Box>
                        )
                    })
                }
            </div>
        </div>
    )

    return (
        isTruncated ? 
        <Tooltip hoverDelay={900} content={data} width='wide' preferredPosition='mostSpace'>
            {endpoint}
        </Tooltip>
        : endpoint
    )
}

export default StyledEndpoint