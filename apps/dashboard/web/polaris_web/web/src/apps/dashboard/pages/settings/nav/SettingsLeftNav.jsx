import { Navigation } from "@shopify/polaris"
import { StoreDetailsFilledMinor, IdentityCardFilledMajor, AutomationFilledMajor, AppsFilledMajor, ListFilledMajor, ReportFilledMinor, LockFilledMajor, CollectionsFilledMajor} from "@shopify/polaris-icons"
import { useLocation, useNavigate } from "react-router-dom"

const SettingsLeftNav = () => {
    const navigate = useNavigate()
    
    const location = useLocation()
    const path = location.pathname
    const page = path.substring(path.lastIndexOf('/') + 1)
    
    return (
        <Navigation>
            <Navigation.Section
                items={[
                    {
                        label: 'About',
                        icon: StoreDetailsFilledMinor,
                        selected: page === "about",
                        onClick: () => navigate("/dashboard/settings/about")
                    },
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
                    {
                        label: 'Logs',
                        icon: ListFilledMajor,
                        selected: page === "logs",
                        onClick: () => navigate("/dashboard/settings/logs")
                    },
                    {
                        label: 'Metrics',
                        icon: ReportFilledMinor,
                        selected: page === "metrics",
                        onClick: () => navigate("/dashboard/settings/metrics")
                    },
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
                ]}
            />
        </Navigation>
    )
}

export default SettingsLeftNav