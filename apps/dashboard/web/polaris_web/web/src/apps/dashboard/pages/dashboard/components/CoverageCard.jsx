import React from 'react'
import transform from '../transform'
import { Box, Card, Divider, HorizontalStack, ProgressBar, Scrollable, Text, VerticalStack } from '@shopify/polaris'

function CoverageCard({coverageObj, collections, collectionsMap}) {

    const sortedCollectionElements = transform.formatCoverageData(coverageObj,collections)
    return (
        <Card>
            <VerticalStack gap={5}>
                <Text variant="bodyLg" fontWeight="semibold">Test coverage</Text>
                <Scrollable style={{maxHeight: '400px'}} shadow> 
                    <Box>
                    {sortedCollectionElements.map((collectionObj,index)=> (
                        <Box padding={2} key={collectionObj.id}>
                            <VerticalStack gap={2}>
                                <Text variant="bodyMd" breakWord truncate>
                                    {collectionsMap[collectionObj.id]}
                                </Text>
                                <HorizontalStack gap={2}>
                                    <Box width='85%'>
                                        <ProgressBar size="small" color={collectionObj.status} progress={collectionObj.coverage} />
                                    </Box>
                                    <Text breakWord color="subdued" variant="bodyMd">{collectionObj.coverage}%</Text>
                                </HorizontalStack>
                                {index < (collections.length - 1) ? <Divider/> : null }
                            </VerticalStack>
                        </Box>
                    ))}
                    </Box>
                </Scrollable>
            </VerticalStack>
        </Card>        
    )
}

export default CoverageCard