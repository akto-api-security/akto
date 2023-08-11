import { Text, Tooltip } from "@shopify/polaris";
import { useRef, useState, useEffect } from "react";
import "./style.css"

function TooltipText(props) {

    // do not send the "as" property as it is being hardcoded to allow using refs.
    const { text, textProps, tooltip } = props;
    console.log(textProps)
    const ref = useRef(null);
    const [isTruncated, setIsTruncated] = useState(false);

    useEffect(() => {
        const element = ref.current;
        setIsTruncated(element.scrollWidth > element.clientWidth);
    }, []);

    const TextContent = (
        <Text as={"span"} truncate {...textProps}>
            <span ref={ref} className="tooltipSpan">
                {text}
            </span>
        </Text>
    )

    return (
        isTruncated ? <Tooltip content={tooltip} hoverDelay={900}>
            {TextContent}
        </Tooltip> :
            TextContent
    );
}

export default TooltipText;