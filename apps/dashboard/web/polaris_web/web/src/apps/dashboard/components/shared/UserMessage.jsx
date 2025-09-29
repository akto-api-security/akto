import { Box, Text } from '@shopify/polaris'

function UserMessage({ message }) {
    return (
        <Box style={{ padding: '20px', maxWidth: '800px', margin: '0 auto' }}>
            <Box style={{ display: 'flex', justifyContent: 'flex-end' }}>
                <Box style={{
                    backgroundColor: '#303030',
                    color: 'white',
                    padding: '12px 16px',
                    borderRadius: '18px',
                    maxWidth: '70%'
                }}>
                    <Text variant="bodyMd" as="span" color="base">
                        <span style={{ color: 'white' }}>{message}</span>
                    </Text>
                </Box>
            </Box>
        </Box>
    )
}

export default UserMessage