import { Tooltip, Text, HorizontalStack, Box } from "@shopify/polaris"
import func from "@/util/func"
import "../api_inventory.css"
import { useRef, useEffect, useState } from "react"

const StyledEndpoint = (data) => {
    const { method, url } = func.toMethodUrlObject(data)
    const arr = url.split("/")
    let colored = []
    arr.forEach((item, index) => {
        if (item.startsWith("{param")) {
            colored.push(index);
        }
    })

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
        setIsTruncated(element.scrollWidth > element.clientWidth);
    }, []);

    const endpoint = (
        <HorizontalStack gap={"1"} wrap={false}>
                <div style={{color: getMethodColor(method), fontSize: '16px', fontWeight: 600}}>
                    {method}
                </div>
                <div className="styled-endpoint" ref={ref}>
                    {
                        arr?.map((item, index) => {
                            return (
                                <Box key={index} as={"span"} color={colored.includes(index) ? "text-critical-active" : ""}>
                                    <Text as="span" variant="headingMd">
                                    {item + "/"}
                                </Text>
                                </Box>
                            )
                        })
                    }
                </div>
            </HorizontalStack>
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