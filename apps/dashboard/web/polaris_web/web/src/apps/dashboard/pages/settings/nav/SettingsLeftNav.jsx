import { Navigation } from "@shopify/polaris"
import { StoreDetailsFilledMinor, IdentityCardFilledMajor, AutomationFilledMajor, AppsFilledMajor, ListFilledMajor, ReportFilledMinor, LockFilledMajor, CollectionsFilledMajor, VocabularyMajor, PlanMajor} from "@shopify/polaris-icons"
import { useLocation, useNavigate } from "react-router-dom"

const SettingsLeftNav = () => {
    const navigate = useNavigate()
    
    const location = useLocation()
    const path = location.pathname
    const page = path.substring(path.lastIndexOf('/') + 1)
    
    const aboutArr = window.IS_SAAS === 'true' ? [] : [{
        label: 'About',
        icon: StoreDetailsFilledMinor,
        selected: page === "about",
        onClick: () => navigate("/dashboard/settings/about")
    }]
    const logsArr = window.IS_SAAS === 'true' ? [] : [{
        label: 'Logs',
        icon: ListFilledMajor,
        selected: page === "logs",
        onClick: () => navigate("/dashboard/settings/logs")
    }]
    const metricsArr = window.IS_SAAS === 'true' ? [] : [{
        label: 'Metrics',
        icon: ReportFilledMinor,
        selected: page === "metrics",
        onClick: () => navigate("/dashboard/settings/metrics")
    }]
    const selfHostedArr = window.IS_SAAS === 'true' ? [{
        label: 'Self hosted',
        icon: PlanMajor,
        selected: page === "self-hosted",
        onClick: () => navigate("/dashboard/settings/self-hosted")
    }] : []

    const billingArr = window.IS_SAAS === 'true' || window.DASHBOARD_MODE === 'ON_PREM' ? [{
        label: 'Billing',
        icon: PlanMajor,
        selected: page === "billing",
        onClick: () => navigate("/dashboard/settings/billing")
     }] : [];

    return (
        <Navigation>
            <Navigation.Section
                items={[
                    ...aboutArr,
                    {
                        label: 'Users',
                        icon: IdentityCardFilledMajor,
                        selected: page === "users",
                        onClick: () => navigate("/dashboard/settings/users")
                    },
                    // {
                    //     label: 'Alerts',
                    //     icon: DiamondAlertMinor,
                    //     selected: page === "alerts",
                    //     onClick: () => navigate("/dashboard/settings")
                    // },
                    {
                        label: 'CI/CD',
                        icon: AutomationFilledMajor,
                        selected: page === "ci-cd",
                        onClick: () => navigate("/dashboard/settings/integrations/ci-cd")
                    },
                    {
                        label: 'Integrations',
                        icon: AppsFilledMajor,
                        selected: page === "integrations",
                        onClick: () => navigate("/dashboard/settings/integrations")
                    },
                    
                    ...logsArr,
                    ...metricsArr,
                    {
                        label: 'Auth types',
                        icon: LockFilledMajor,
                        selected: page === "auth-types",
                        onClick: () => navigate("/dashboard/settings/auth-types")
                    },
                    {
                        label: 'Tags',
                        icon: CollectionsFilledMajor,
                        selected: page === "tags",
                        onClick: () => navigate("/dashboard/settings/tags")
                    },
                    {
                        label: 'Test library',
                        icon: VocabularyMajor,
                        selected: page === "test-library",
                        onClick: () => navigate("/dashboard/settings/test-library")
                    },
                    ...billingArr,
                    ...selfHostedArr
                ]}
            />
        </Navigation>
    )
}

export default SettingsLeftNav