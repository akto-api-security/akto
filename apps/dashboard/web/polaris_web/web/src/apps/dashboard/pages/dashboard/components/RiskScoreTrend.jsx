import React, { useEffect, useRef, useState } from 'react'
import NullData from './NullData'
import { Badge, Box, Button, Card, Divider, InlineGrid, InlineStack, Link, Text, BlockStack } from '@shopify/polaris'
import HighchartsReact from 'highcharts-react-official'
import transform from '../transform'
import Highcharts from "highcharts"
import TitleWithInfo from '@/apps/dashboard/components/shared/TitleWithInfo'
import { useNavigate } from 'react-router-dom'

function RiskScoreTrend({riskScoreRangeMap, riskScoreRanges}) {

    const navigate = useNavigate()

    const [showComponent, setShowComponent] = useState()
    const riskScoreTrendRef = useRef(null)

    const nullComponent = (
        <NullData text={"APIs by risk score"} url={"/dashboard/observe/inventory"} urlText={"to create a collection and upload traffic in it."} description={"No apis found."} key={"riskScoreNullTrend"}/>
    )

    const dataComponent = (
        <Card key="scoreTrend">
            <BlockStack gap={5}>
                <TitleWithInfo
                    titleText={"APIs by risk score"}
                    tooltipContent={"All your endpoints grouped on the basis of their risk score."}
                    textProps={{variant: "headingMd"}}
                    docsUrl={"https://docs.akto.io/api-inventory/concepts/risk-score"}
                />
                <InlineGrid columns={2} gap={5}>
                    <HighchartsReact
                        highcharts={Highcharts}
                        options={transform.getRiskScoreTrendOptions(riskScoreRangeMap, riskScoreRanges, navigate)}
                        ref={riskScoreTrendRef}
                    />
                    <Box paddingInlineEnd={4} paddingInlineStart={4} paddingBlockEnd={2} paddingBlockStart={2}>
                        <BlockStack gap={3}>
                            {riskScoreRanges.map((range)=>{
                                return (
                                    <BlockStack gap={1} key={range.text}>
                                        <InlineStack align="space-between">
                                            <Button


                                                removeUnderline
                                                onClick={() => navigate(`/dashboard/observe/inventory/${range.apiCollectionId}`)}
                                                variant="monochromePlain">
                                                    <Text variant="bodyMd" color="semibold" >{range.text}</Text>
                                            </Button>
                                            <Badge tone={range.status}>{range.range}</Badge>
                                        </InlineStack>
                                        <Divider />
                                    </BlockStack>
                                );
                            })}
                        </BlockStack>
                    </Box>
                </InlineGrid>
            </BlockStack>
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