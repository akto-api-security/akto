import { LegacyCard, Select, Text } from "@shopify/polaris"
import { useEffect, useState } from "react";
import settingRequests from "../api";

const Logs = () => {

    const [logGroup, setLogGroup] = useState('')

    const handleSelectLogGroup = (logGroup) => setLogGroup(logGroup) 

    const logGroupOptions = [
        { label: "Testing", value: "TESTING" },
        { label: "Runtime", value: "RUNTIME" },
        { label: "Dashboard", value: "DASHBOARD" },
    ];

    const fetchLogsFromDb = async () => {
        let fiveMins = 1000 * 60 * 5
        let startTime = Date.now() - fiveMins
        let endTime = Date.now()

        if (logGroup !== '')
        {
            const logsResponse = await settingRequests.fetchLogsFromDb(startTime, endTime, logGroup)
            console.log(logsResponse)
        }
    }

    useEffect(() => {
        fetchLogsFromDb()
    }, [logGroup])

    fetchLogsFromDb()

    return (
        <LegacyCard
            sectioned
            title="Logs"
            actions={[
                { content: 'Export' },
                { content: 'Configure log level' }
            ]}
        >
            <Text variant="bodyMd">
                API logs capture detailed records of API requests and responses, including metadata such as timestamps, request headers, payload data, and authentication details.
            </Text>
            <br />
            <Select
                labelHidden
                placeholder="Select log group level"
                options={logGroupOptions}
                onChange={handleSelectLogGroup}
                value={logGroup}
            />
        </LegacyCard>
    )
}

export default Logs