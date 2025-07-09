import React, { useEffect, useState } from 'react'
import SmoothAreaChart from '../../dashboard/new_components/SmoothChart'
import SummaryCard from '../../dashboard/new_components/SummaryCard'
import observeFunc from "../../observe/transform.js"
import func from "@/util/func";
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

    const getIssuesGraphData = async () => {
        const issuesGraphDataRes = await api.findTotalIssuesByDay(startTimestamp, endTimestamp)
        const testCoverageDataRes = await api.fetchTestCoverageData(startTimestamp, endTimestamp)
        
        const testCoverageData = testCoverageDataRes.historicalData.map(item => {
            const totalCoverageVal = (((100 * item.apisTested) / item.totalApis).toFixed(2))
            return parseFloat(totalCoverageVal)
        })
        
        const {
            totalIssuesCountDayWise,
            openIssuesCountDayWise,
            criticalIssuesCountDayWise,
        } = issuesGraphDataRes
    
        observeFunc.setIssuesState(totalIssuesCountDayWise, setTotalIssues, setTotalIssuesDelta, true)
        observeFunc.setIssuesState(openIssuesCountDayWise, setOpenIssues, setOpenIssuesDelta, true)
        observeFunc.setIssuesState(criticalIssuesCountDayWise, setCriticalIssues, setCriticalIssuesDelta, true)
        observeFunc.setIssuesState(testCoverageData, setTestCoverage, setTestCoverageDelta, false)
    }

    useEffect(() => {
        getIssuesGraphData()
    }, [startTimestamp, endTimestamp])

    const summaryInfo = [
        {
            title: 'Total issues',
            data: observeFunc.formatNumberWithCommas(totalIssues[totalIssues.length-1]),
            variant: 'heading2xl',
            byLineComponent: observeFunc.generateByLineComponent(totalIssuesDelta, func.timeDifference(startTimestamp, endTimestamp)),
            smoothChartComponent: (<SmoothAreaChart tickPositions={totalIssues} />)
        },
        {
            title: 'Open',
            data: observeFunc.formatNumberWithCommas(openIssues[openIssues.length-1]),
            variant: 'heading2xl',
            color: 'critical',
            byLineComponent: observeFunc.generateByLineComponent(openIssuesDelta, func.timeDifference(startTimestamp, endTimestamp)),
            smoothChartComponent: (<SmoothAreaChart tickPositions={openIssues} />)
        },
        {
            title: 'Critical/High',
            data: criticalIssues[criticalIssues.length-1],
            variant: 'heading2xl',
            color: 'critical',
            byLineComponent: observeFunc.generateByLineComponent(criticalIssuesDelta, func.timeDifference(startTimestamp, endTimestamp)),
            smoothChartComponent: (<SmoothAreaChart tickPositions={criticalIssues} />),
            tooltipContent: "Total number of CRITICAL + HIGH severity open issues"
        },
        {
            title: 'Test Coverage',
            data: (testCoverage[testCoverage.length-1] || 0).toFixed(2) + "%",
            variant: 'heading2xl',
            color: 'warning',
            byLineComponent: observeFunc.generateByLineComponent(testCoverageDelta.toFixed(2), func.timeDifference(startTimestamp, endTimestamp)),
            smoothChartComponent: (<SmoothAreaChart tickPositions={testCoverage} />)
        }
    ]

    return (
        <SummaryCard summaryItems={summaryInfo} />
    )
}

export default SummaryInfo