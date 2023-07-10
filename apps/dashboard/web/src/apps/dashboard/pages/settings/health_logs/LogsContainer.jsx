import { Scrollable, Text } from "@shopify/polaris";
import func from "../../../../../util/func";

const LogsContainer = ({ logs }) => {

    const logsFetchBetween = () => {
        let d1 = func.epochToDateTime(Math.floor(logs.startTime / 1000))
        let d2 = func.epochToDateTime(Math.floor(logs.endTime / 1000))
        return "Fetched logs from " + d1 + " to " + d2;
    }

    const logContent = []
    for (let i = 0; i < logs.logData.length; i++) {
        const timestamp = func.epochToDateTime(logs.logData[i].timestamp)
        const log = logs.logData[i].log
        logContent.push("[" + timestamp + "]" + " " + log)
    }

    return (
        <div>
             <div>
                {logsFetchBetween()}
            </div>
            <br />

            <Scrollable style={{height: '60vh'}}>
               {logContent.map((logText, idx) => (
                <div key={idx}>
                    <Text variant="bodyMd-mono">
                        {logText}
                    </Text>
                    <br />
                </div>
               ))}
            </Scrollable>
        </div>
    )
}

export default LogsContainer