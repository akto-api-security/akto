import { Page, Tabs } from "@shopify/polaris"
import { useState } from "react"
import ModuleInfo from "./ModuleInfo"
import TokensTab from "./TokensTab"
import { usePermissions } from "@/util/permissions"

const TABS = [
    { id: 'modules', content: 'Modules' },
    { id: 'tokens', content: 'Tokens' }
]

const ModuleInfoPage = () => {
    const { canCall } = usePermissions()
    const [selectedTab, setSelectedTab] = useState(0)
    // only admins can list tokens; everyone else sees the Modules tab alone
    const canSeeTokens = canCall('api/fetchAuthTokens')
    const tabs = canSeeTokens ? TABS : TABS.slice(0, 1)
    const activeTab = canSeeTokens ? selectedTab : 0

    return (
        <Page
            title="Module Information"
            divider
        >
            {canSeeTokens && <Tabs tabs={tabs} selected={activeTab} onSelect={setSelectedTab} />}
            {activeTab === 0 ? <ModuleInfo /> : <TokensTab />}
        </Page>
    )
}

export default ModuleInfoPage
