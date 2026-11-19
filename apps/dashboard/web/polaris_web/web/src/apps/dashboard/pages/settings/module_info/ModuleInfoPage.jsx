import { Box } from "@shopify/polaris"
import PageWithMultipleCards from "../../../components/layouts/PageWithMultipleCards"
import LayoutWithTabs from "../../../components/layouts/LayoutWithTabs"
import TitleWithInfo from "../../../components/shared/TitleWithInfo"
import ModuleInfo from "./ModuleInfo"
import TokensTab from "./TokensTab"
import { usePermissions } from "@/util/permissions"

const ModuleInfoPage = () => {
    const { canCall } = usePermissions()
    // only admins can list tokens; everyone else sees the modules table alone, without tabs
    const canSeeTokens = canCall('api/fetchAuthTokens')

    const tabs = [
        { id: 'modules', content: 'Modules', component: <ModuleInfo /> },
        { id: 'authentication-tokens', content: 'Authentication tokens', component: <TokensTab /> },
    ]

    return (
        <PageWithMultipleCards
            title={<TitleWithInfo titleText="Module Information" />}
            isFirstPage={true}
            components={[
                canSeeTokens
                    ? <Box width="100%" key="module-info-tabs"><LayoutWithTabs tabs={tabs} currTab={() => {}} /></Box>
                    : <ModuleInfo key="module-info" />
            ]}
        />
    )
}

export default ModuleInfoPage
