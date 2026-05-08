import {Box} from '@shopify/polaris'
import { MarkdownRenderer, markdownStyles } from './MarkdownComponents'


const MarkdownViewer = ({markdown}) => {

    return (
        <Box paddingBlockStart={3} paddingInlineEnd={4} paddingInlineStart={4}>
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