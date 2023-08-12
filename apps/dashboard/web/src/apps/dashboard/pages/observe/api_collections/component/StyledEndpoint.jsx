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
            case "GET": return "text-info";
            case "POST": return "text-primary";
            case "PUT": return "text-interactive";
            case "PATCH": return "text-magic";
            case "DELETE": return "text-warning-strong";
            case "OPTIONS": return "text-caution-strong";
            case "HEAD": return "text-caution";
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
                <Box color={getMethodColor(method)}>
                    <Text as="span" variant="headingMd" >
                        {method}
                    </Text>
                </Box>
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