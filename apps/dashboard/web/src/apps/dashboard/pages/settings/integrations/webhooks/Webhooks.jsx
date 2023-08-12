import { useEffect, useState } from "react"
import settingRequests from "../../api"
import IntegrationsLayout from "../IntegrationsLayout"
import SpinnerCentered from "../../../../components/progress/SpinnerCentered"
import GithubSimpleTable from "../../../../components/tables/GithubSimpleTable"
import func from "@/util/func"
import { Button } from "@shopify/polaris"
import { useNavigate } from "react-router-dom"
import WebhooksStore from "./webhooksStore"
import Store from "../../../../store"

function Webhooks() {
    const navigate = useNavigate()

    const [isLoading, setIsLoading] = useState()
    const setToastConfig = Store(state => state.setToastConfig)
    const customWebhooks = WebhooksStore(state => state.customWebhooks)
    const setCustomWebhooks = WebhooksStore(state => state.setCustomWebhooks)

    async function fetchCustomWebhooks() {
        setIsLoading(true)
        const customWebhooksResponse = await settingRequests.fetchCustomWebhooks()
        if (customWebhooksResponse) {
            const CustomWebhooks = customWebhooksResponse.customWebhooks

            // remove null values from a given custom webhook object
            const filterCustomWebhooks = CustomWebhooks.map(obj => 
                Object.fromEntries(Object.entries(obj).filter(([k, v]) => v !== null))
            )

            // prettify custom webhook data
            const mapCustomWebhooks = filterCustomWebhooks.map(customWebhook => ({
                ...customWebhook,
                createTime: func.prettifyEpoch(customWebhook.createTime),
                lastSentTimestamp: func.prettifyEpoch(customWebhook.createTime),
                nextUrl: `${customWebhook.id}`
            }))
            
            setCustomWebhooks(mapCustomWebhooks)
            setIsLoading(false)
        }
    }

    useEffect(() => {
        fetchCustomWebhooks()
    }, [])

    const webhooksCardContent = "Webhooks integration"

    const resourceName = {
        singular: 'Webhook',
        plural: 'Webhhooks',
    };
    
    const headers = [
        {
            text: "Name",
            value: "webhookName",
            showFilter: true,
            itemOrder: 1,
        },
        {
            text: "Create time",
            value: "createTime",
            showFilter: true,
            itemCell: 2,
        },
        {
            text: "Status",
            value: "activeStatus",
            showFilter: true,
            itemCell: 2,
        },
        {
            text: "Last Sent",
            value: "lastSentTimestamp",
            showFilter: true,
            itemCell: 2,
        },
    ]

    const sortOptions = [
        { label: 'Name', value: 'webhookName asc', directionLabel: 'A-Z', sortKey: 'webhookName' },
        { label: 'Name', value: 'webhookName desc', directionLabel: 'Z-A', sortKey: 'webhookName' },
      ];
    
    async function handleWebhookStatusChange(id, status) {
        const webhookStatusChangeResponse = await settingRequests.changeStatus(id, status)
        if (webhookStatusChangeResponse) {
            setToastConfig({ isActive: true, isError: false, message: `Webhook ${status === "ACTIVE" ? "activated" : "deactivated"}` })
            fetchCustomWebhooks()
        }
    }
    
    const getActions = (item) => {
        return [{
            items: [{
                content: item.activeStatus === "ACTIVE" ? 'Deactivate' : 'Activate',
                onAction: () => handleWebhookStatusChange(item.id, item.activeStatus === "ACTIVE" ? "INACTIVE": "ACTIVE"),
            }]
        }]
    }

    function disambiguateLabel(key, value) {
        switch (key) {
            case "createTime":
            case "lastSentTimestamp":
                return func.convertToDisambiguateLabel(value, func.prettifyEpoch, 2)
            default:
                return func.convertToDisambiguateLabelObj(value, null, 2)
          }          
    }

    const WebhooksCard = (
        <div>
            {
                isLoading ?
                    <SpinnerCentered /> :
                    <div style={{ paddingTop: "20px" }}>
                        <GithubSimpleTable
                            key="table"
                            data={customWebhooks}
                            sortOptions={sortOptions}
                            resourceName={resourceName}
                            filters={[]}
                            disambiguateLabel={disambiguateLabel}
                            headers={headers}
                            hasRowActions={true}
                            getActions={getActions}
                         />
                    </div>
            }
        </div>

    )

    const createCustomWebhook =  (
        <Button onClick={() => navigate('create_custom_webhook')}>
            Create Custom Webhook
        </Button>
    )

    return (
        <IntegrationsLayout
            title="Webhooks"
            cardContent={webhooksCardContent}
            component={WebhooksCard}
            secondaryAction={createCustomWebhook}
        />
    )
}

export default Webhooks 