import { Navigation } from "@shopify/polaris"
import {
    TextInRowsFilledIcon,
    IdentityCardFilledIcon,
    AutomationFilledIcon,
    AppsFilledIcon,
    ComposeIcon,
    ListBulletedFilledIcon,
    SearchResourceIcon,
    LockFilledIcon,
    CollectionFilledIcon,
    PlanIcon,
    ChatIcon,
    VariantIcon,
    BookOpenIcon,
    AdjustIcon,
} from "@shopify/polaris-icons";
import { useLocation, useNavigate } from "react-router-dom"
import func from "@/util/func"

const SettingsLeftNav = () => {
    const navigate = useNavigate()
    
    const location = useLocation()
    const path = location.pathname
    const page = path.substring(path.lastIndexOf('/') + 1)

    const usersArr = window.USER_ROLE !== 'GUEST' ? [{
        label: 'Users',
        icon: IdentityCardFilledIcon,
        selected: page === "users",
        onClick: () => navigate("/dashboard/settings/users")
    }] : []
    const logsArr = window?.IS_SAAS !== 'true' ||
        (window?.USER_NAME && window?.USER_NAME.includes("akto")) ? [{
            label: 'Logs',
            icon: ListBulletedFilledIcon,
            selected: page === "logs",
            onClick: () => navigate("/dashboard/settings/logs")
        }] : []
    const metricsArr = window.IS_SAAS === 'true' ? [] : [{
        label: 'Metrics',
        icon: SearchResourceIcon,
        selected: page === "metrics",
        onClick: () => navigate("/dashboard/settings/metrics")
    }]
    const selfHostedArr = window.IS_SAAS === 'true' ? [{
        label: 'Self hosted',
        icon: PlanIcon,
        selected: page === "self-hosted",
        onClick: () => navigate("/dashboard/settings/self-hosted")
    }] : []
    const auditLogsArr = ((window.IS_SAAS === 'true' || window.DASHBOARD_MODE === 'ON_PREM') && window.USER_ROLE === 'ADMIN') ? [{
        label: 'Audit logs',
        icon: ComposeIcon,
        selected: page === 'audit-logs',
        onClick: () => navigate("/dashboard/settings/audit-logs")
    }] : []

    const billingArr = window.IS_SAAS === 'true' || window.DASHBOARD_MODE === 'ON_PREM' ? [{
        label: 'Billing',
        icon: PlanIcon,
        selected: page === "billing",
        onClick: () => navigate("/dashboard/settings/billing")
     }] : [];

    const cicdArr = !func.checkLocal() ? [{
        label: 'CI/CD',
        icon: AutomationFilledIcon,
        selected: page === "ci-cd",
        onClick: () => navigate("/dashboard/settings/integrations/ci-cd")
    }] : [];

    return (
        <Navigation>
            <Navigation.Section
                items={[
                    {
                        label: 'About',
                        icon: TextInRowsFilledIcon,
                        selected: page === "about",
                        onClick: () => navigate("/dashboard/settings/about")
                    },
                    ...usersArr,
                    // {
                    //     label: 'Alerts',
                    //     icon: DiamondAlertMinor,
                    //     selected: page === "alerts",
                    //     onClick: () => navigate("/dashboard/settings")
                    // },
                    ...cicdArr,
                    {
                        label: 'Integrations',
                        icon: AppsFilledIcon,
                        selected: page === "integrations",
                        onClick: () => navigate("/dashboard/settings/integrations")
                    },
                    
                    ...logsArr,
                    ...metricsArr,
                    {
                        label: 'Auth types',
                        icon: LockFilledIcon,
                        selected: page === "auth-types",
                        onClick: () => navigate("/dashboard/settings/auth-types")
                    },
                    {
                        label: 'Default payloads',
                        icon: VariantIcon,
                        selected: page === "default-payloads",
                        onClick: () => navigate("/dashboard/settings/default-payloads")
                    },
                    {
                        label: 'Advanced traffic filters',
                        icon: AdjustIcon,
                        selected: page === "advanced-filters",
                        onClick: () => navigate("/dashboard/settings/advanced-filters")
                    }, 
                    {
                        label: 'Tags',
                        icon: CollectionFilledIcon,
                        selected: page === "tags",
                        onClick: () => navigate("/dashboard/settings/tags")
                    },
                    {
                        label: 'Test library',
                        icon: BookOpenIcon,
                        selected: page === "test-library",
                        onClick: () => navigate("/dashboard/settings/test-library")
                    },
                    ...billingArr,
                    ...selfHostedArr,
                    ...auditLogsArr,
                    {
                        label: 'Help & Support',
                        icon: ChatIcon,
                        selected: page === "help",
                        onClick: () => navigate("/dashboard/settings/help")
                    }
                ]}
            />
        </Navigation>
    );
}

export default SettingsLeftNav