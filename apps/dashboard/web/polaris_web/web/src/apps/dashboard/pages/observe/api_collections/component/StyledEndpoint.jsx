import { Tooltip, Text, Box } from "@shopify/polaris"
import func from "@/util/func"
import "../api_inventory.css"
import { useRef, useEffect, useState } from "react"
import transform from "../../transform"

const StyledEndpoint = (data, fontSize, variant, shouldNotTruncate, showWithoutHost) => {
    const { method, url } = func.toMethodUrlObject(data)
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

    function getMethodColor(method) {
        switch (method) {
            case "GET": return `var(--color-get)`;
            case "POST": return `var(--color-post)`;
            case "PUT": return `var(--color-put)`;
            case "PATCH": return `var(--color-patch)`;
            case "DELETE": return `var(--color-delete)`;
            case "OPTIONS": return `var(--color-options)`;
            case "HEAD": return `var(--color-head)`;
            default:
                return "";
        }
    }

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
            <div style={{color: getMethodColor(method), fontSize: finalFontSize, fontWeight: 600}}>
                {method}
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