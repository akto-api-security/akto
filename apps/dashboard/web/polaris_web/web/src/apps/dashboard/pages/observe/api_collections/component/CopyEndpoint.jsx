import { Avatar, Box, Button, Tooltip } from "@shopify/polaris";
import func from "@/util/func";
import { useRef } from "react";

const CopyEndpoint = (apiDetail) => {
    const ref = useRef(null)
    return(
        <Box paddingBlockStart={"05"}>
            <Button plain onClick={() => func.copyToClipboard(apiDetail, ref, "URL copied")}>
                <Tooltip content="Copy endpoint" dismissOnMouseOut>
                    <div className="reduce-size">
                        <Avatar size="extraSmall" source="/public/copy_icon.svg" />
                    </div>
                </Tooltip>
                <Box ref={ref} />
            </Button>
        </Box>
    ) 
}

export default CopyEndpoint