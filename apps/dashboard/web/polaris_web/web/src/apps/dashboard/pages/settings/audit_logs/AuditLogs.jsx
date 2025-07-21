import { Badge, Button, IndexFiltersMode, Text } from '@shopify/polaris'
import React, { useEffect, useReducer, useState } from 'react'
import settingRequests from '../api'
import func from '@/util/func'
import PageWithMultipleCards from '../../../components/layouts/PageWithMultipleCards'
import TitleWithInfo from '../../../components/shared/TitleWithInfo'
import DateRangeFilter from '../../../components/layouts/DateRangeFilter'
import { produce } from 'immer'
import values from "@/util/values";
import GithubSimpleTable from '../../../components/tables/GithubSimpleTable'
import { CellType } from '../../../components/tables/rows/GithubRow'
import { saveAs } from 'file-saver'

const headers = [
    {
        title: "User email",
        text: "User email",
        value: "userEmail",
        filterKey: "userEmail",
        showFilter: true
    },
    {
        title: "Operation",
        text: "Operation",
        value: "apiEndpoint",
        filterKey: "apiEndpoint",
        showFilter: true
    },
    {
        title: "Operation description",
        text: "Operation description",
        value: "actionDescription"
    },
    {
        title: "Time",
        text: "Time",
        value: "timestamp",
        sortActive: true,
    },
    {
        title: "User IP address",
        text: "User IP address",
        value: "userIpAddress",
        filterKey: "userIpAddress",
        showFilter: true
    },
    {
        title: "User agent",
        text: "User agent",
        value: "userAgent",
        filterKey: "rawUserAgent",
        isText: CellType.TEXT,
    },
]

const sortOptions = [
    { label: 'Timestamp', value: 'timestamp asc', directionLabel: 'Newest', sortKey: 'rawTimestamp', columnIndex: 4 },
    { label: 'Timestamp', value: 'timestamp desc', directionLabel: 'Oldest', sortKey: 'rawTimestamp', columnIndex: 4 },
]

const resourceName = {
    singular: 'audit log',
    plural: 'audit logs',
}

const AuditLogs = () => {
    const [key, setKey] = useState(false)
    const [loading, setLoading] = useState(false)
    const [tableLoading, setTableLoading] = useState(false)
    const [auditLogsData, setAuditLogsData] = useState([])
    
    const [currDateRange, dispatchCurrDateRange] = useReducer(produce((draft, action) => func.dateRangeReducer(draft, action)), values.ranges[2])
    const getTimeEpoch = (key) => {
        return Math.floor(Date.parse(currDateRange.period[key]) / 1000)
    }
    const startTimestamp = getTimeEpoch("since")
    const endTimestamp = getTimeEpoch("until")

    const fetchData = async () => {
        setLoading(true)
        setTableLoading(true)
        await settingRequests.fetchApiAuditLogsFromDb(0, -1, 1, startTimestamp, endTimestamp).then((resp) => {
            const apiAuditLogsArr = resp?.apiAuditLogs.map((item) => {
                const { apiEndpoint, actionDescription, timestamp, userAgent } = item
                return {
                    ...item,
                    apiEndpoint: func.formatEndpoint(apiEndpoint),
                    actionDescription: <Text>{actionDescription}</Text>,
                    timestamp: func.prettifyEpoch(timestamp),
                    rawTimestamp: timestamp,
                    userAgent: <Badge>{func.toSentenceCase(userAgent)}</Badge>,
                    rawUserAgent: userAgent
                }
            })
        
            setAuditLogsData(apiAuditLogsArr)
        }).finally(() => {
            setLoading(false)
            setTableLoading(false)
        })

    }

    useEffect(() => {
        fetchData()
    }, [startTimestamp, endTimestamp])

    const exportAuditLogsCSV = async () => {
        setLoading(true)
        if(auditLogsData == null || auditLogsData.length === 0) {
            func.setToast(true, true, "Can not export empty CSV")
            setLoading(false)
            return
        }
        const csvHeaders = Object.keys(auditLogsData[0]).join(',') + '\n'
        const csvRows = auditLogsData.map(row => {
            return Object.values(row).join(',')
        }).join('\n')
    
        const csvString = csvHeaders + csvRows
        let blob = new Blob([csvString], {
            type: "application/csvcharset=UTF-8"
        });
        saveAs(blob, "akto_audit_logs.csv") ;
        func.setToast(true, false,"CSV exported successfully")
        setLoading(false)
    }

    function disambiguateLabel(key, value) {
        return func.convertToDisambiguateLabelObj(value, null, 2)
    }

    const auditLogsTable = (
        <GithubSimpleTable
            key={key}
            pageLimit={20}
            data={auditLogsData}
            resourceName={resourceName}
            sortOptions={sortOptions}
            filters={[]}
            appliedFilters={[]}
            headers={headers}
            headings={headers}
            selectable={false}
            useNewRow={true}
            condensedHeight={true}
            disambiguateLabel={disambiguateLabel}
            mode={IndexFiltersMode.Filtering}
            onRowClick={(data) => {}}
            showFooter={false}
            loading={tableLoading}
        />
    )

    return (
        <PageWithMultipleCards
            title={
                <TitleWithInfo 
                    titleText={"Audit logs"} 
                />
            }
            primaryAction={<Button loading={loading} primary onClick={exportAuditLogsCSV}>Export as CSV</Button>}
            secondaryActions={<DateRangeFilter initialDispatch={currDateRange} dispatch={(dateObj) => dispatchCurrDateRange({ type: "update", period: dateObj.period, title: dateObj.title, alias: dateObj.alias })} />}
            isFirstPage={true}
            components={[auditLogsTable]}
        />
    )
}

export default AuditLogs