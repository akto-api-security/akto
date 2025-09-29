import { Box, Text } from '@shopify/polaris'
import Markdown from 'react-markdown'

function AIMessage({ message }) {
    return (
        <Box style={{
            backgroundColor: '#f8f9fa',
            padding: '20px'
        }}>
            <Box style={{ maxWidth: '800px', margin: '0 auto' }}>
                <Box style={{ display: 'flex', gap: '12px' }}>
                    <Box style={{
                        width: '28px',
                        height: '28px',
                        backgroundColor: '#10a37f',
                        borderRadius: '4px',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        flexShrink: 0
                    }}>
                        <span style={{
                            color: 'white',
                            fontSize: '14px',
                            fontWeight: '600'
                        }}>
                            AI
                        </span>
                    </Box>
                    <Box style={{ flex: 1, paddingTop: '2px' }}>
                        <Markdown>{message}</Markdown>
                    </Box>
                </Box>
            </Box>
        </Box>
    )
}

export default AIMessage