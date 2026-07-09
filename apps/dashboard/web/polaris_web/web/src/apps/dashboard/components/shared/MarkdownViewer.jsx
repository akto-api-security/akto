import {Box} from '@shopify/polaris'
import { MarkdownRenderer, markdownStyles } from './MarkdownComponents'


const MarkdownViewer = ({markdown, noPadding}) => {

    return (
        <Box paddingBlockStart={noPadding ? undefined : 3} paddingInlineEnd={noPadding ? undefined : 4} paddingInlineStart={noPadding ? undefined : 4}>
            <div className="markdown-content">
                <MarkdownRenderer>
                    {markdown}
                </MarkdownRenderer>
            </div>
            <style jsx>{`
                ${markdownStyles}
            `}</style>
        </Box>
    )
}


export default MarkdownViewer