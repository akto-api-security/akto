import {Box, DataTable,HorizontalStack, Scrollable, Text, VerticalStack } from '@shopify/polaris'
import React from 'react'
import DonutChart from '../../../components/shared/DonutChart'
import ConcentricCirclesChart from '../../../components/shared/ConcentricCirclesChart'
import observeFunc from "../../observe/transform"
import TooltipText from '../../../components/shared/TooltipText'

function ChartypeComponent({data, title,charTitle, chartSubtitle, reverse, isNormal, boxHeight, navUrl, isRequest, chartOnLeft, dataTableWidth, boxPadding, pieInnerSize, chartSize, spaceBetween, navUrlBuilder}) {
    let tableRows = []
    if(data && Object.keys(data).length > 0)
    {
        Object.keys(data).forEach((key,index)=>{
            let comp = [
                (
                    <Box>
                        <div
                            style={{display: "flex", gap: "8px", alignItems: "center", maxWidth: '200px'}}
                            key={index}
                        >
                            <span style={{background: data[key]?.color, borderRadius: "50%", width: "8px", height: "8px"}} />
                            <Box width='150px'>
                                <TooltipText tooltip={key} text={key}/>
                            </Box>
                        </div>
                    </Box>
                ),
                <HorizontalStack gap={1} wrap={false}>
                    <Box
                        width='30px'
                    >
                        <Text>{observeFunc.formatNumberWithCommas(data[key]?.text)}</Text>
                    </Box>
                    {data[key].dataTableComponent ? data[key].dataTableComponent : null}
                </HorizontalStack>
            ]
            tableRows.push(comp)
        })
    }

    const chartData = reverse ? Object.keys(data).reverse().reduce((acc, key) => {
        acc[key] = data[key];
        return acc;
      }, {}) : data

    const chartComponent = (

        isNormal ? <DonutChart navUrl={navUrl} navUrlBuilder={navUrlBuilder} data={chartData}  title={charTitle}  subtitle={chartSubtitle} type={title} size={chartSize || 210} isRequest={isRequest} pieInnerSize={pieInnerSize}/> : <ConcentricCirclesChart data={chartData} title={charTitle} size={210} subtitle={chartSubtitle} />
    )

    return (
        <Box className={"issues-severity-graph-container"} padding={boxPadding !== undefined ? boxPadding : 4}>
            <HorizontalStack wrap={false} gap={8} align={spaceBetween || ''}>
                {chartOnLeft ? chartComponent: null}
                <VerticalStack gap="2">
                    <Text fontWeight="semibold" variant="bodySm">{title}</Text>
                    <Scrollable style={{maxHeight: boxHeight}} focusable shadow>
                        <Box className={"issues-severity-graph-table-container"} width={dataTableWidth ? dataTableWidth : '260px'}>
                            <DataTable headings={[]}
                                columnContentTypes={[
                                    'text',
                                    'numeric'
                                ]}
                                rows={tableRows}
                                increasedTableDensity
                                hoverable={false}
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