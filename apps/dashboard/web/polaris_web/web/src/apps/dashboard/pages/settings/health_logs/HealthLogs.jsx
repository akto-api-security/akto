import { Page } from "@shopify/polaris"
import Logs from "./Logs"


const HealthLogs = () => {

    return (
        <Page
            title="Logs"
            divider
            fullWidth
        >
            <Logs />
        </Page>
    )
}

export default HealthLogs