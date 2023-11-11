import React from 'react'
import transform from '../transform'
import PersistStore from '../../../../main/PersistStore'
import { Box, Card, Text, VerticalStack } from '@shopify/polaris'

function CoverageCard({coverageObj}) {

    const sortedCollectionElements = transform.formatCoverageData(coverageObj)
    const collectionsMap = PersistStore(state =>  state.collectionsMap)
    return (
        <Card>
            <VerticalStack gap={5}>
                <Text variant="bodyLg" fontWeight="semibold">Test coverage</Text>
                <Box>
                {sortedCollectionElements.map((collectionObj,index)=> (
                    <Box padding={2}>
                        <VerticalStack gap={2}>
                            <Text variant="bodyMd">
                                {collectionsMap[collectionObj.id]}
                            </Text>
                        </VerticalStack>
                    </Box>
                ))}
                </Box>
            </VerticalStack>
        </Card>        
    )
}

export default CoverageCard