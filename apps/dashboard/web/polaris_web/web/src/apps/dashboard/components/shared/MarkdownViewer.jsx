import Markdown from 'react-markdown'
import {Box} from '@shopify/polaris'


const MarkdownViewer = ({markdown}) => {

    return (
        <Box paddingBlockStart={3} paddingInlineEnd={4} paddingInlineStart={4}>
            <Markdown>{markdown}</Markdown>
        </Box>
    )
}


export default MarkdownViewer