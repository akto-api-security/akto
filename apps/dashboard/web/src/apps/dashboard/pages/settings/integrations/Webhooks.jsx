import { useEffect, useState } from "react"
import settingRequests from "../api"
import IntegrationsLayout from "./IntegrationsLayout"
import SpinnerCentered from "../../../components/progress/SpinnerCentered"

function Webhooks () {
    const [ isLoading, setIsLoading ] = useState()
    const [ customWebhooks, setCustomWebhooks ] = useState()

    async function fetchCustomWebhooks() {
        setIsLoading(true)
        const customWebhooksResponse = await settingRequests.fetchCustomWebhooks()
        setCustomWebhooks(customWebhooksResponse)
        setIsLoading(false)
    }

    useEffect(() => {
        fetchCustomWebhooks()
    }, [])

    const webhooksCardContent = "Webhooks integration"

    const WebhooksCard = (
        <div>
            {
                isLoading ? 
                <SpinnerCentered /> :
                <div>
                    WebhooksCard
                </div>
            }
        </div>
        
    )

    return (
       <IntegrationsLayout
            title="Webhooks"
            cardContent={webhooksCardContent}
            component={WebhooksCard}
       />
    )
}

export default Webhooks 