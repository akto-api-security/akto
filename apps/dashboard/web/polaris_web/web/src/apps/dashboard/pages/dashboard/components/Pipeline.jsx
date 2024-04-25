import { Card, DataTable, HorizontalStack, Scrollable, Text, VerticalStack, Button } from '@shopify/polaris'
import React from 'react'
import transform from '../transform'

function Pipeline({riskScoreMap, collections, collectionsMap, showcicd }) {

    const tableRows = transform.prepareTableData(riskScoreMap,collections, collectionsMap);

    return (
        <Card>
            <VerticalStack gap={5}>
                <VerticalStack gap={2}>
                    <Text variant="bodyLg" fontWeight="semibold">Add in your CI/CD pipeline</Text>
                    <Text>Seamlessly enhance your web application security with CI/CD integration, empowering you to efficiently detect vulnerabilities, analyze and intercept web traffic, and fortify your digital defenses.</Text>
                </VerticalStack>
                <Scrollable style={{maxHeight: '200px', paddingBottom:'10px'}} shadow>
                    <DataTable headings={[]}
                        columnContentTypes={[
                            'text',
                            'numeric'
                        ]}
                        rows={tableRows}
                        increasedTableDensity
                        truncate
                    /> 
                </Scrollable>
                <VerticalStack gap={5}>
             
                <HorizontalStack gap={4}>
                <Button onClick={showcicd} > Create Token</Button> 
                <span style={{color:"#3385ff"}} > Learn More</span>
                </HorizontalStack>
                </VerticalStack>

            </VerticalStack>
          
        </Card>
    )
}

export default Pipeline