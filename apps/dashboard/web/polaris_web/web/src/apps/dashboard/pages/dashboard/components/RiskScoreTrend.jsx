import React, { useEffect, useRef, useState } from 'react'
import NullData from './NullData'
import { Badge, Box, Card, Divider, HorizontalGrid, HorizontalStack, Text, VerticalStack } from '@shopify/polaris'
import HighchartsReact from 'highcharts-react-official'
import transform from '../transform'
import Highcharts from "highcharts"

function RiskScoreTrend({riskScoreRangeMap, riskScoreRanges}) {

    const [showComponent, setShowComponent] = useState()
    const riskScoreTrendRef = useRef(null)

    const nullComponent = (
        <NullData text={"APIS by risk score"} url={"/dashboard/observe/inventory"} urlText={"to create a collection and upload traffic in it."} description={"No apis found."} key={"riskScoreNullTrend"}/>
    )

    const dataComponent = (
        <Card key="scoreTrend">
            <VerticalStack gap={5}>
                <Text variant="bodyLg" fontWeight="semibold">APIS by risk score</Text>
                <HorizontalGrid columns={2} gap={5}>
                <HighchartsReact
                    highcharts={Highcharts}
                    options={transform.getRiskScoreTrendOptions(riskScoreRangeMap)}
                    ref={riskScoreTrendRef}
                />
                <Box paddingInlineEnd={4} paddingInlineStart={4} paddingBlockEnd={2} paddingBlockStart={2}>
                    <VerticalStack gap={3}>
                        {riskScoreRanges.map((range)=>{
                            return(
                                <VerticalStack gap={1} key={range.text}>
                                    <HorizontalStack align="space-between">
                                        <Text variant="bodyMd">{range.text}</Text>
                                        <Badge status={range.status}>{range.range}</Badge>
                                    </HorizontalStack>
                                    <Divider />
                                </VerticalStack>
                            )
                        })}
                    </VerticalStack>
                </Box>
                </HorizontalGrid>
            </VerticalStack>
        </Card>
    )

    useEffect(() => {
        if(Object.keys(riskScoreRanges).length === 0){
            setShowComponent(nullComponent)
        }else{
            setShowComponent(dataComponent)
        }
    },[riskScoreRanges])

  return (
        showComponent
  )
}

export default RiskScoreTrend