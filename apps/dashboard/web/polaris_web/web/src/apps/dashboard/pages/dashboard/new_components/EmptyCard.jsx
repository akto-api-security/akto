
import { Card, VerticalStack, Text, Box, Avatar, HorizontalStack } from '@shopify/polaris';
import ComponentHeader from './ComponentHeader';

function EmptyCard({ title, subTitleComponent, itemId, onRemoveComponent, tooltipContent }) {
    const showHeader = itemId && onRemoveComponent;

    return (
        <Card padding={4} key="info">
            <VerticalStack gap={3}>
                {showHeader && <ComponentHeader title={title} itemId={itemId} onRemove={onRemoveComponent} tooltipContent={tooltipContent} />}
                <div style={{width: "60%", height: "100%", display: "flex", alignItems: "center", justifyContent: "center", margin: "auto", minHeight: showHeader ? "200px" : "344px"}}>
                    <VerticalStack gap={3}>
                        <HorizontalStack align='center'>
                            <Box width='64px' minHeight='64px' borderRadius="full" background="bg-subdued">
                                <div style={{'height': '64px', 'width': '64px', 'alignItems': 'center', 'display': 'flex', 'justifyContent': 'center'}}>
                                    <Avatar source="/public/analytics.svg" size="medium" shape='round'/>
                                </div>
                            </Box>
                        </HorizontalStack>
                        {!showHeader && <Text fontWeight='semibold' variant='headingLg' alignment='center'>{title}</Text>}
                        {subTitleComponent}
                    </VerticalStack>
                </div>
            </VerticalStack>
        </Card>
    );
}

export default EmptyCard;
