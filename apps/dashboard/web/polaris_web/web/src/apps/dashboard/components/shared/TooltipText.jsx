import { Text, Tooltip } from "@shopify/polaris";
import { useRef, useState, useEffect } from "react";
import "./style.css"

function TooltipText(props) {

    // do not send the "as" property as it is being hardcoded to allow using refs.
    const { text, textProps, tooltip, highlightOnHover } = props;
    const ref = useRef(null);
    const [isTruncated, setIsTruncated] = useState(false);

    useEffect(() => {
        const element = ref.current;
        setIsTruncated(element.scrollWidth > element.clientWidth);
    }, []);

    const TextContent = (
        <div className="tooltipSpan" ref={ref}>
            <Text as={"span"} {...textProps}>
                {text}
            </Text>
        </div>
    )

    return (
        isTruncated ? <Tooltip content={tooltip} hoverDelay={400} width="wide">
            {TextContent}
        </Tooltip> :
            TextContent
    );
}

export default TooltipText;