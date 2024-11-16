import { Scrollable, Spinner, BlockStack } from "@shopify/polaris";
import func from "@/util/func";
import { useTheme } from "@shopify/polaris"

const LogsContainer = ({ logs }) => {
    const theme = useTheme();
    const logsFetchBetween = () => {
        let d1 = func.epochToDateTime(Math.floor(logs.startTime / 1000))
        let d2 = func.epochToDateTime(Math.floor(logs.endTime / 1000))

        return (
            <span>
                <span>Fetched logs from </span>
                <span style={{color: theme.color["color-bg-fill-success"]}}>{d1}</span>
                <span > to </span>
                <span style={{color: theme.color["color-bg-fill-success"]}}>{d2}</span>
            </span>
        )
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
            <br/>

            <Scrollable style={{maxHeight: '40vh'}}>
               {logContent.map((logText, idx) => (
                <BlockStack gap={1} key={idx}>
                    <div style={{fontFamily:theme.font["font-family-mono"], fontWeight: theme.font["font-weight-medium"],fontSize: '12px', letterSpacing: "0px", textAlign: "left"}}>
                        {logText}
                    </div>
                </BlockStack>
               ))}
            </Scrollable>
        </div>
    );
}

export default LogsContainer