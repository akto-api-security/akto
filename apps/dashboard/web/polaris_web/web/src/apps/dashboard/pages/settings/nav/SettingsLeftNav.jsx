import { Navigation } from "@shopify/polaris"
import { StoreDetailsFilledMinor, IdentityCardFilledMajor, AutomationFilledMajor, AppsFilledMajor, ComposeMajor, ProfileMajor} from "@shopify/polaris-icons"
import { ListFilledMajor, ReportFilledMinor, LockFilledMajor, CollectionsFilledMajor, PlanMajor, ChatMajor} from "@shopify/polaris-icons"
import { VariantMajor, VocabularyMajor, AdjustMinor, UndoMajor } from "@shopify/polaris-icons"
import { useLocation, useNavigate } from "react-router-dom"
import func from "@/util/func"

const SettingsLeftNav = () => {
    const navigate = useNavigate()
    
    const location = useLocation()
    const path = location.pathname
    const page = path.substring(path.lastIndexOf('/') + 1)
    let rbacAccess = func.checkForRbacFeatureBasic();
    let rbacAccessAdvanced = func.checkForRbacFeature();

    const usersArr = window.USER_ROLE !== 'GUEST' ? [{
        label: 'Users',
        icon: IdentityCardFilledMajor,
        selected: page === "users",
        onClick: () => navigate("/dashboard/settings/users")
    }] : []

    const roleArr = window.USER_ROLE === 'ADMIN' && rbacAccess && rbacAccessAdvanced ? [{
        label: 'Roles',
        icon: ProfileMajor,
        selected: page === "roles",
        onClick: () => navigate("/dashboard/settings/roles")
    }] : []

    const logsArr = window?.IS_SAAS !== 'true' ||
        (window?.USER_NAME && window?.USER_NAME.includes("akto")) ? [{
            label: 'Logs',
            icon: ListFilledMajor,
            selected: page === "logs",
            onClick: () => navigate("/dashboard/settings/logs")
        }] : []
    const metricsArr = window.DASHBOARD_MODE !== 'ON_PREM' ? [{
        label: 'Metrics',
        icon: ReportFilledMinor,
        selected: page === "metrics",
        onClick: () => navigate("/dashboard/settings/metrics")
    }] : []
    const selfHostedArr = window.IS_SAAS === 'true' ? [{
        label: 'Self hosted',
        icon: PlanMajor,
        selected: page === "self-hosted",
        onClick: () => navigate("/dashboard/settings/self-hosted")
    }] : []
    const auditLogsArr = ((window.IS_SAAS === 'true' || window.DASHBOARD_MODE === 'ON_PREM') && window.USER_ROLE === 'ADMIN') ? [{
        label: 'Audit logs',
        icon: ComposeMajor,
        selected: page === 'audit-logs',
        onClick: () => navigate("/dashboard/settings/audit-logs")
    }] : []

    const billingArr = window.IS_SAAS === 'true' || window.DASHBOARD_MODE === 'ON_PREM' ? [{
        label: 'Billing',
        icon: PlanMajor,
        selected: page === "billing",
        onClick: () => navigate("/dashboard/settings/billing")
     }] : [];

    const cicdArr = !func.checkLocal() ? [{
        label: 'CI/CD',
        icon: AutomationFilledMajor,
        selected: page === "ci-cd",
        onClick: () => navigate("/dashboard/settings/integrations/ci-cd")
    }] : [];

    const threatConfigArr = window?.STIGG_FEATURE_WISE_ALLOWED?.THREAT_DETECTION?.isGranted ? [{
        label: 'Threat Configuration',
        icon: AutomationFilledMajor,
        selected: page === "threat-configuration",
        onClick: () => navigate("/dashboard/settings/threat-configuration")
    }] : [];

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
                    ...usersArr,
                    ...roleArr,
                    ...threatConfigArr,
                    // {
                    //     label: 'Alerts',
                    //     icon: DiamondAlertMinor,
                    //     selected: page === "alerts",
                    //     onClick: () => navigate("/dashboard/settings")
                    // },
                    {
                        label: 'Undo Demerged APIs',
                        icon: UndoMajor,
                        selected: page === 'undo-demerge-apis',
                        onClick: () => navigate("/dashboard/settings/undo-demerge-apis")
                    },
                    ...cicdArr,
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
                        label: 'Default payloads',
                        icon: VariantMajor,
                        selected: page === "default-payloads",
                        onClick: () => navigate("/dashboard/settings/default-payloads")
                    },
                    {
                        label: 'Advanced traffic filters',
                        icon: AdjustMinor,
                        selected: page === "advanced-filters",
                        onClick: () => navigate("/dashboard/settings/advanced-filters")
                    }, 
                    {
                        label: 'Test library',
                        icon: VocabularyMajor,
                        selected: page === "test-library",
                        onClick: () => navigate("/dashboard/settings/test-library")
                    },
                    ...billingArr,
                    ...selfHostedArr,
                    ...auditLogsArr,
                    {
                        label: 'Help & Support',
                        icon: ChatMajor,
                        selected: page === "help",
                        onClick: () => navigate("/dashboard/settings/help")
                    }
                ]}
            />
        </Navigation>
    )
}

export default SettingsLeftNav