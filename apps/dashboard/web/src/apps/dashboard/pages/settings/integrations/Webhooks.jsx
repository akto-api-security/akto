import { useEffect, useState } from "react"
import settingRequests from "../api"
import IntegrationsLayout from "./IntegrationsLayout"
import SpinnerCentered from "../../../components/progress/SpinnerCentered"
import GithubSimpleTable from "../../../components/tables/GithubSimpleTable"
import func from "@/util/func"

function Webhooks() {
    const [isLoading, setIsLoading] = useState()
    const [customWebhooks, setCustomWebhooks] = useState([])

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
                lastSentTimestamp: func.prettifyEpoch(customWebhook.createTime)
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
        singular: 'Sensitive data type',
        plural: 'Sensitive data types',
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
    
    
    const getActions = (item) => {
        return [{
            items: [{
                content: 'Edit',
                onAction: () => navigate("/dashboard/observe/data-types", {state: {name: item.subType, dataObj: mapData[item.subType], type: item.isCustomType ? 'Custom' : 'Akto'}}),
            }]
        }]
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
                            disambiguateLabel={() => { }}
                            headers={headers}
                            hasRowActions={true}
                            getActions={getActions}
                            rowClickable={true}
                         />
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