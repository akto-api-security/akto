import { Box, Button, TextField, Tooltip } from '@shopify/polaris'
import { ClipboardMinor } from "@shopify/polaris-icons"
import React, { useRef } from 'react'

import func from "@/util/func";

function CopyCommand({command}) {

    const ref = useRef(null) ;
    const copyContent = () => {
        func.copyToClipboard(command, ref, "Command copied.");
    }

    return (
        <Box background="bg-subdued" padding={2} borderRadius="1">
            <TextField 
                connectedRight={(<Tooltip dismissOnMouseOut content="Copy URL">
                    <Box>
                        <div ref={ref} />
                        <Button onClick={copyContent} icon={ClipboardMinor} />
                    </Box>
                </Tooltip>)}
                value={command}
            />
        </Box>
    )
}

export default CopyCommand