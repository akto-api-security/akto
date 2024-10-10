import { Box, HorizontalStack, Icon, Text } from '@shopify/polaris'
import React, { useEffect, useState } from 'react'
import SmoothAreaChart from '../../dashboard/new_components/SmoothChart'
import SummaryCard from '../../dashboard/new_components/SummaryCard'
import observeFunc from "../../observe/transform.js"
import func from "@/util/func";
import { ArrowUpMinor, ArrowDownMinor } from '@shopify/polaris-icons'
import api from '../api.js'

const SummaryInfo = ({ startTimestamp, endTimestamp }) => {
    const [totalIssues, setTotalIssues] = useState([])
    const [totalIssuesDelta, setTotalIssuesDelta] = useState(0)
    const [openIssues, setOpenIssues] = useState([])
    const [openIssuesDelta, setOpenIssuesDelta] = useState(0)
    const [criticalIssues, setCriticalIssues] = useState([])
    const [criticalIssuesDelta, setCriticalIssuesDelta] = useState(0)
    const [testCoverage, setTestCoverage] = useState([])
    const [testCoverageDelta, setTestCoverageDelta] = useState(0)

    const getCumulativeData = (data) => {
        let cumulative = []
        data.reduce((acc, val, i) => cumulative[i] = acc + val, 0)
        return cumulative
    }

    const getIssuesGraphData = async () => {
        const issuesGraphDataRes = await api.findTotalIssuesByDay(startTimestamp, endTimestamp)
        const testCoverageDataRes = await api.fetchTestCoverageData(startTimestamp, endTimestamp)
        
        const testCoverageData = testCoverageDataRes.historicalData.map(item => Math.round(100 * item.apisTested / item.totalApis))
        
        const {
            totalIssuesCountDayWise,
            openIssuesCountDayWise,
            criticalIssuesCountDayWise,
        } = issuesGraphDataRes
    
        const setIssuesState = (data, setState, setDelta, useCumulative) => {
            if(useCumulative) {
                data = getCumulativeData(data)
            }

            if (data == null || data.length === 0) {
                setState([0, 0])
                setDelta(0)
            } else if (data.length === 1) {
                setState([0, data[0]])
                setDelta(data[0])
            } else {
                setState(data)
                
                setDelta(data[data.length-1] - data[0])
            }
        }
    
        setIssuesState(totalIssuesCountDayWise, setTotalIssues, setTotalIssuesDelta, true)
        setIssuesState(openIssuesCountDayWise, setOpenIssues, setOpenIssuesDelta, true)
        setIssuesState(criticalIssuesCountDayWise, setCriticalIssues, setCriticalIssuesDelta, true)
        setIssuesState(testCoverageData, setTestCoverage, setTestCoverageDelta, false)
    }

    useEffect(() => {
        getIssuesGraphData()
    }, [startTimestamp, endTimestamp])

    const generateByLineComponent = (val, time) => {
        if (!val || isNaN(val)) return null
        if (val === 0 ) {
            return <Text>No change in {time}</Text>
        }
        const source = val > 0 ? ArrowUpMinor : ArrowDownMinor
        return (
            <HorizontalStack gap={1}>
                <Box>
                    <Icon source={source} color='subdued' />
                </Box>
                <Text color='subdued' fontWeight='medium'>{Math.abs(val)}</Text>
                <Text color='subdued' fontWeight='semibold'>{time}</Text>
            </HorizontalStack>
        )
    }

    const summaryInfo = [
        {
            title: 'Total issues',
            data: observeFunc.formatNumberWithCommas(totalIssues[totalIssues.length-1]),
            variant: 'heading2xl',
            byLineComponent: generateByLineComponent(totalIssuesDelta, func.timeDifference(startTimestamp, endTimestamp)),
            smoothChartComponent: (<SmoothAreaChart tickPositions={totalIssues} />)
        },
        {
            title: 'Open',
            data: observeFunc.formatNumberWithCommas(openIssues[openIssues.length-1]),
            variant: 'heading2xl',
            color: 'critical',
            byLineComponent: generateByLineComponent(openIssuesDelta, func.timeDifference(startTimestamp, endTimestamp)),
            smoothChartComponent: (<SmoothAreaChart tickPositions={openIssues} />)
        },
        {
            title: 'Critical',
            data: criticalIssues[criticalIssues.length-1],
            variant: 'heading2xl',
            color: 'critical',
            byLineComponent: generateByLineComponent(criticalIssuesDelta, func.timeDifference(startTimestamp, endTimestamp)),
            smoothChartComponent: (<SmoothAreaChart tickPositions={criticalIssues} />)
        },
        {
            title: 'Test Coverage',
            data: (testCoverage[testCoverage.length-1] || 0).toFixed(2) + "%",
            variant: 'heading2xl',
            color: 'warning',
            byLineComponent: generateByLineComponent(testCoverageDelta.toFixed(2), func.timeDifference(startTimestamp, endTimestamp)),
            smoothChartComponent: (<SmoothAreaChart tickPositions={testCoverage} />)
        }
    ]

    return (
        <SummaryCard summaryItems={summaryInfo} />
    )
}

export default SummaryInfo