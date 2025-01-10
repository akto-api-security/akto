import {Box, DataTable,InlineStack, Scrollable, Text, BlockStack } from '@shopify/polaris'
import React from 'react'
import DonutChart from '../../../components/shared/DonutChart'
import ConcentricCirclesChart from '../../../components/shared/ConcentricCirclesChart'
import observeFunc from "../../observe/transform"
import TooltipText from '../../../components/shared/TooltipText'

function ChartypeComponent({data, title,charTitle, chartSubtitle, reverse, isNormal, boxHeight, navUrl, isRequest, chartOnLeft, dataTableWidth, boxPadding, pieInnerSize, chartSize, spaceBetween}) {
    let tableRows = []
    if(data && Object.keys(data).length > 0)
    {
        Object.keys(data).forEach((key,index)=>{
            let comp = [
                (
                    <Box >
                        <div style={{display: "flex", gap: "8px", alignItems: "center", maxWidth: '200px'}} key={index}>
                            <span style={{background: data[key]?.color, borderRadius: "50%", width: "8px", height: "8px",minWidth: "8px", minHeight: "8px"}} />
                            <TooltipText tooltip={key} text={key}/>
                        </div>
                    </Box>
                ),
                <InlineStack gap={100} wrap={false}>
                    <Box width='30px'>
                        <Text>{observeFunc.formatNumberWithCommas(data[key]?.text)}</Text>
                    </Box>
                    {data[key].dataTableComponent ? data[key].dataTableComponent : null}
                </InlineStack>
            ]
            tableRows.push(comp)
        })
    }

    const chartData = reverse ? Object.keys(data).reverse().reduce((acc, key) => {
        acc[key] = data[key];
        return acc;
      }, {}) : data

    const chartComponent = (

        isNormal ? <DonutChart navUrl={navUrl} data={chartData}  title=""  type={title} size={chartSize || 210} isRequest={isRequest} pieInnerSize={pieInnerSize}/> : <ConcentricCirclesChart data={chartData} title={charTitle} size={210} subtitle={chartSubtitle} />
    )

    return (
        <Box padding={boxPadding != undefined ? boxPadding : 400}>
            <InlineStack gap={800} align={spaceBetween || ''}>
                {chartOnLeft ? chartComponent: null}
                <BlockStack gap="200">
                    <Text fontWeight="semibold" variant="bodySm">{title}</Text>
                    <Scrollable style={{maxHeight: boxHeight}} focusable shadow>
                        <Box width={dataTableWidth ? dataTableWidth : '260px'}>
                            <DataTable headings={[]}
                                columnContentTypes={[
                                    'text',
                                    'numeric'
                                ]}
                                rows={tableRows}
                                increasedTableDensity
                                hoverable={false}
                                hideScrollIndicator={true}
                            />
                        </Box>
                    </Scrollable>
                </BlockStack>
                {!chartOnLeft ? chartComponent: null}
            </InlineStack>
        </Box>
    );
}

export default ChartypeComponent