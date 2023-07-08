import { Page } from "@shopify/polaris"
import Metrics from "./Metrics"
import Logs from "./Logs"


const HealthLogs = () => {

    return (
        <Page
            title="Health & Logs"
            divider
        >
            <Metrics />
            <br />
            <Logs />
        </Page>
    )
}

export default HealthLogs