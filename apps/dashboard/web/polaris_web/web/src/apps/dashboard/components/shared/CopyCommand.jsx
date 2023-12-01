import { Box, Button, TextField, Tooltip } from '@shopify/polaris'
import { ClipboardMinor } from "@shopify/polaris-icons"
import React from 'react'

import func from "@/util/func";

function CopyCommand({command}) {

    const copyContent = () => {
        navigator.clipboard.writeText(command)
        func.setToast(true, false, "Command copied !")
    }

    return (
        <Box background="bg-subdued" padding={2} borderRadius="1">
            <TextField 
                connectedRight={(<Tooltip dismissOnMouseOut content="Copy URL">
                    <Button onClick={copyContent} icon={ClipboardMinor} />
                </Tooltip>)}
                value={command}
            />
        </Box>
    )
}

export default CopyCommand