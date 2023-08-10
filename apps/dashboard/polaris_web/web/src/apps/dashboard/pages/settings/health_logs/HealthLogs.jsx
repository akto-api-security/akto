import { Page } from "@shopify/polaris"
import Logs from "./Logs"


const HealthLogs = () => {

    return (
        <Page
            title="Health & Logs"
            divider
        >
            <br />
            <Logs />
        </Page>
    )
}

export default HealthLogs