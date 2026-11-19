import { forwardRef } from "react"
import { Box } from "@shopify/polaris"

// A scroll region of fixed height. Box has no height prop, so the outer Box reserves the height
// with minHeight (it has no in-flow children, so it never grows past it) and the absolutely
// positioned inner Box fills it and scrolls. The ref points at the scrolling element.
const ScrollArea = forwardRef(function ScrollArea({ height, padding = "4", children, ...ariaProps }, ref) {
    return (
        <Box position="relative" minHeight={height}>
            <Box
                ref={ref}
                position="absolute"
                insetBlockStart="0"
                insetBlockEnd="0"
                insetInlineStart="0"
                insetInlineEnd="0"
                overflowY="auto"
                padding={padding}
                {...ariaProps}
            >
                {children}
            </Box>
        </Box>
    )
})

export default ScrollArea
