import {Box, DataTable,HorizontalStack, Scrollable, Text, VerticalStack } from '@shopify/polaris'
import React from 'react'
import DonutChart from '../../../components/shared/DonutChart'
import ConcentricCirclesChart from '../../../components/shared/ConcentricCirclesChart'

function ChartypeComponent({data, title,charTitle, chartSubtitle}) {
    let tableRows = []
    Object.keys(data).forEach((key,index)=>{
        let comp = [
            (
                <Box maxWidth='210px' width='210px'>
                    <HorizontalStack gap={2} key={index}>
                        <span style={{background: data[key]?.color, borderRadius: "50%", width: "8px", height: "8px"}} />
                        <Text>{key}</Text>
                    </HorizontalStack>
                </Box>
            ),
            <Text>{data[key]?.text}</Text>
        ]
        tableRows.push(comp)
    })

    const chartComponent = (
        title === 'Categories' ? <DonutChart data={data} title="" size={210}/> : <ConcentricCirclesChart data={data} title={charTitle} size={210} subtitle={chartSubtitle} />
    )

    return (
        <Box padding={4}>
            <HorizontalStack gap={8}>
                <VerticalStack gap="2">
                    <Text fontWeight="semibold" variant="bodySm">{title}</Text>
                    <Scrollable style={{maxHeight: '200px'}} focusable shadow>
                        <Box width='260px'>
                            <DataTable headings={[]}
                                columnContentTypes={[
                                    'text',
                                    'numeric'
                                ]}
                                rows={tableRows}
                                increasedTableDensity
                                truncate
                            />
                        </Box>
                    </Scrollable>
                </VerticalStack>
                {chartComponent}
            </HorizontalStack>
        </Box>
    )
}

export default ChartypeComponent