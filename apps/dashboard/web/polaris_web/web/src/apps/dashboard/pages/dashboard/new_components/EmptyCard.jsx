
import {  Card, VerticalStack, Text, Icon, Box, Avatar, HorizontalStack, Link} from '@shopify/polaris';
import React from 'react';

function EmptyCard({ title, subTitleComponent }) {
    return (
        <Card padding={4} key="info">
            <div style={{width: "60%", height: "100%", display: "flex", alignItems: "center", justifyContent: "center", margin: "auto", minHeight: "344px"}}>
                <VerticalStack gap={3}>
                    <HorizontalStack align='center'>
                        <Box width='64px' minHeight='64px' borderRadius="full" background="bg-subdued">
                            <div style={{'height': '64px', 'width': '64px', 'alignItems': 'center', 'display': 'flex', 'justifyContent': 'center'}}>
                                <Avatar source="/public/analytics.svg" size="medium" shape='round'/>
                            </div>
                        </Box>
                    </HorizontalStack>
                    <Text fontWeight='semibold' variant='headingLg' alignment='center'>{title}</Text>
                    {subTitleComponent}
                </VerticalStack>
            </div>
        </Card>
    );
}

export default EmptyCard;
