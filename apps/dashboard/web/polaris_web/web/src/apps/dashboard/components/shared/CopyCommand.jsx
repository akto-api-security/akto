import { Box, Button, TextField, Tooltip } from '@shopify/polaris'
import { ClipboardIcon } from "@shopify/polaris-icons";
import React, { useRef } from 'react'

import func from "@/util/func";

function CopyCommand({command}) {

    const ref = useRef(null) ;
    const copyContent = () => {
        func.copyToClipboard(command, ref, "Command copied.");
    }

    return (
        <Box background="bg-subdued" padding={200} borderRadius="1">
            <TextField 
                connectedRight={(<Tooltip dismissOnMouseOut content="Copy URL">
                    <Box>
                        <div ref={ref} />
                        <Button onClick={copyContent} icon={ClipboardIcon} size='large'/>
                    </Box>
                </Tooltip>)}
                value={command}
            />
        </Box>
    );
}

export default CopyCommand