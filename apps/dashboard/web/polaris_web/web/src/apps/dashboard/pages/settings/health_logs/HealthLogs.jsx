import PageWithMultipleCards from "../../../components/layouts/PageWithMultipleCards"
import Logs from "./Logs"
import { Text } from "@shopify/polaris"


const HealthLogs = () => {

    return (
        <PageWithMultipleCards
            divider={true}
            components={[<Logs key="logs" />]}
            title={
                <Text variant='headingLg' truncate>
                    Logs
                </Text>
            }
            isFirstPage={true}
        />
    )
}

export default HealthLogs