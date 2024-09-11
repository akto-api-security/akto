import {Box, DataTable,HorizontalStack, Scrollable, Text, VerticalStack } from '@shopify/polaris'
import React from 'react'
import DonutChart from '../../../components/shared/DonutChart'
import ConcentricCirclesChart from '../../../components/shared/ConcentricCirclesChart'
import observeFunc from "../../observe/transform"

function ChartypeComponent({data, title,charTitle, chartSubtitle, reverse, isNormal, boxHeight, navUrl, isRequest, chartOnLeft}) {
    let tableRows = []
    if(data && Object.keys(data).length > 0)
    {
        Object.keys(data).forEach((key,index)=>{
            let comp = [
                (
                    <Box width='22vw'>
                        <div style={{display: "flex", gap: "8px", alignItems: "center"}} key={index}>
                            <span style={{background: data[key]?.color, borderRadius: "50%", width: "8px", height: "8px"}} />
                            <Text>{key}</Text>
                        </div>
                    </Box>
                ),
                <Text>{observeFunc.formatNumberWithCommas(data[key]?.text)}</Text>
            ]
            tableRows.push(comp)
        })
    }

    const chartData = reverse ? Object.keys(data).reverse().reduce((acc, key) => {
        acc[key] = data[key];
        return acc;
      }, {}) : data

    const chartComponent = (

        isNormal ? <DonutChart navUrl={navUrl} data={chartData}  title=""  type={title} size={210} isRequest={isRequest}/> : <ConcentricCirclesChart data={chartData} title={charTitle} size={210} subtitle={chartSubtitle} />
    )

    return (
        <Box padding={4}>
            <HorizontalStack gap={8}>
                {chartOnLeft ? chartComponent: null}
                <VerticalStack gap="2">
                    <Text fontWeight="semibold" variant="bodySm">{title}</Text>
                    <Scrollable style={{maxHeight: boxHeight}} focusable shadow>
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
                {!chartOnLeft ? chartComponent: null}
            </HorizontalStack>
        </Box>
    )
}

export default ChartypeComponent