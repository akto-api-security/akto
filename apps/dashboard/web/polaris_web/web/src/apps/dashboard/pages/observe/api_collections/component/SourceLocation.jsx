import { Box, Text, Tooltip, Link } from '@shopify/polaris'
import React from 'react'
import TooltipText from '../../../../components/shared/TooltipText'

function SourceLocation(props) {
    const { location } = props

    //process file path
    let truncatedFilePath = location.filePath
    const filePath = location.filePath
    const filePathParts = filePath.split('/');
    if (filePathParts.length > 2) {
        const topDirectory = filePathParts[0]
        const fileName = filePathParts[filePathParts.length - 1]
        truncatedFilePath = `${topDirectory}/.../${fileName}`
    }

    const attachLineNo = location.lineNo !== -1 ? `:${location.lineNo}` : ""
    const filePathText = truncatedFilePath + attachLineNo
    const tooltipText = location.filePath

    return (
            <Box width="200px">
                <Tooltip content={tooltipText} preferredPosition="below">
                    {location.fileLink === "" ?
                        <Text variant="bodyMd" fontWeight="medium">{filePathText}</Text> :
                        <Link onClick={(e) => e.stopPropagation()} url={location.fileLink} monochrome target="_blank">
                            <TooltipText tooltip={filePathText} text={filePathText}/>
                        </Link> 
                    }
                </Tooltip>
            </Box>
    )
}

export default SourceLocation