
import {  Card, BlockStack, Text, Icon, Box, Avatar, InlineStack, Link} from '@shopify/polaris';
import React from 'react';

function EmptyCard({ title, subTitleComponent }) {
    return (
        <Card padding={4} key="info">
            <div style={{width: "60%", height: "100%", display: "flex", alignItems: "center", justifyContent: "center", margin: "auto", minHeight: "344px"}}>
                <BlockStack gap={3}>
                    <InlineStack align='center'>
                        <Box width='64px' minHeight='64px' borderRadius="full" background="bg-subdued">
                            <div style={{'height': '64px', 'width': '64px', 'alignItems': 'center', 'display': 'flex', 'justifyContent': 'center'}}>
                                <Avatar source="/public/analytics.svg" size="md" shape='round'/>
                            </div>
                        </Box>
                    </InlineStack>
                    <Text fontWeight='semibold' variant='headingLg' alignment='center'>{title}</Text>
                    {subTitleComponent}
                </BlockStack>
            </div>
        </Card>
    );
}

export default EmptyCard;
