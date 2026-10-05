import { Navigation, Box } from "@shopify/polaris"
import { StoreDetailsFilledMinor, IdentityCardFilledMajor, AutomationFilledMajor, AppsFilledMajor, ComposeMajor, ProfileMajor} from "@shopify/polaris-icons"
import { ListFilledMajor, ReportFilledMinor, LockFilledMajor, PlanMajor, ChatMajor} from "@shopify/polaris-icons"
import { VariantMajor, VocabularyMajor, AdjustMinor, UndoMajor, CodeMajor, GlobeMajor } from "@shopify/polaris-icons"
import { useLocation, useNavigate } from "react-router-dom"
import func from "@/util/func"
import { usePermissions } from "@/util/permissions"
import Store from "../../../store";
import PersistStore from "../../../../main/PersistStore"
import { CATEGORY_ENDPOINT_SECURITY } from "../../../../main/labelHelper"

const SettingsLeftNav = () => {
    const navigate = useNavigate()
    
    const location = useLocation()
    const path = location.pathname
    const page = path.substring(path.lastIndexOf('/') + 1)
    let rbacAccess = func.checkForRbacFeatureBasic();
    let rbacAccessAdvanced = func.checkForRbacFeature();
    const accounts = Store(state => state.accounts) || {};
    const activeAccount = Store(state => state.activeAccount);

    const usersArr = window.USER_ROLE !== 'GUEST' ? [{
        label: 'Users',
        icon: IdentityCardFilledMajor,
        selected: page === "users",
        route: "/dashboard/settings/users",
        onClick: () => navigate("/dashboard/settings/users")
    }] : []

    const roleArr = window.USER_ROLE === 'ADMIN' && rbacAccess && rbacAccessAdvanced ? [{
        label: 'Roles',
        icon: ProfileMajor,
        selected: page === "roles" || path.includes("/settings/roles/"),
        route: "/dashboard/settings/roles",
        onClick: () => navigate("/dashboard/settings/roles")
    }] : []

    const logsArr = window?.IS_SAAS !== 'true' ||
        (window?.USER_NAME && (window?.USER_NAME.includes("akto") || window?.USER_NAME.includes("mongodb"))) ? [{
            label: 'Logs',
            icon: ListFilledMajor,
            selected: page === "logs",
            route: "/dashboard/settings/logs",
            onClick: () => navigate("/dashboard/settings/logs")
        }] : []
    const moduleInfoArr = [{
            label: 'Module Info',
            icon: CodeMajor,
            selected: page === "module-info",
            route: "/dashboard/settings/module-info",
            onClick: () => navigate("/dashboard/settings/module-info")
        }]
    const jobInfoArr = [{
            label: 'Job Info',
            icon: AutomationFilledMajor,
            selected: page === "job-info",
            route: "/dashboard/settings/job-info",
            onClick: () => navigate("/dashboard/settings/job-info")
        }]
    const metricsArr = window.DASHBOARD_MODE !== 'ON_PREM' ? [{
        label: 'Metrics',
        icon: ReportFilledMinor,
        selected: page === "metrics",
        route: "/dashboard/settings/metrics",
        onClick: () => navigate("/dashboard/settings/metrics")
    }] : []
    const hideBillingAndSelfHosted = String(window.ACTIVE_ACCOUNT) === '1786073624' && !window.USER_NAME?.toLowerCase()?.endsWith("@akto.io")

    const selfHostedArr = (window.IS_SAAS === 'true' && !hideBillingAndSelfHosted) ? [{
        label: 'Self hosted',
        icon: PlanMajor,
        selected: page === "self-hosted",
        route: "/dashboard/settings/self-hosted",
        onClick: () => navigate("/dashboard/settings/self-hosted")
    }] : []
    const auditLogsArr = ((window.IS_SAAS === 'true' || window.DASHBOARD_MODE === 'ON_PREM') && func.isUserAdmin()) ? [{
        label: 'Audit logs',
        icon: ComposeMajor,
        selected: page === 'audit-logs',
        route: "/dashboard/settings/audit-logs",
        onClick: () => navigate("/dashboard/settings/audit-logs")
    }] : []

    const billingArr = (window.IS_SAAS === 'true' || window.DASHBOARD_MODE === 'ON_PREM') && !hideBillingAndSelfHosted ? [{
        label: 'Billing',
        icon: PlanMajor,
        selected: page === "billing",
        route: "/dashboard/settings/billing",
        onClick: () => navigate("/dashboard/settings/billing")
     }] : [];

    const cicdArr = !func.checkLocal() ? [{
        label: 'CI/CD',
        icon: AutomationFilledMajor,
        selected: page === "ci-cd",
        route: "/dashboard/settings/integrations/ci-cd",
        onClick: () => navigate("/dashboard/settings/integrations/ci-cd")
    }] : [];

    const threatConfigArr = window?.STIGG_FEATURE_WISE_ALLOWED?.THREAT_DETECTION?.isGranted ? [{
        label: 'Threat Configuration',
        icon: AutomationFilledMajor,
        selected: page === "threat-configuration",
        route: "/dashboard/settings/threat-configuration",
        onClick: () => navigate("/dashboard/settings/threat-configuration")
    }] : [];

    const dashboardCategory = PersistStore((state) => state.dashboardCategory) || "API Security";
    const { canOpen } = usePermissions();
    // pages the role can't open are left out (the same rule shows "Access restricted" when opened by URL)
    const withoutPagesTheRoleCantOpen = (items) => items
        .filter(item => !item.route || canOpen(item.route))
        .map(({ route, ...item }) => item);

    return (
        <Navigation>
            <Navigation.Section
                items={withoutPagesTheRoleCantOpen([
                    {
                        label: (
                            <Box paddingBlockEnd={"2"}>
                                {`Account Name: ${accounts[activeAccount]}`}
                            </Box>
                        )
                    },
                    {
                        label: 'About',
                        icon: StoreDetailsFilledMinor,
                        selected: page === "about",
                        route: "/dashboard/settings/about",
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
                        route: "/dashboard/settings/undo-demerge-apis",
                        onClick: () => navigate("/dashboard/settings/undo-demerge-apis")
                    },
                    ...cicdArr,
                    {
                        label: 'Integrations',
                        icon: AppsFilledMajor,
                        selected: page === "integrations",
                        route: "/dashboard/settings/integrations",
                        onClick: () => navigate("/dashboard/settings/integrations")
                    },
                    {
                        label: 'Browser Extension',
                        icon: GlobeMajor,
                        selected: page === "browser-extension",
                        route: "/dashboard/settings/browser-extension",
                        onClick: () => navigate("/dashboard/settings/browser-extension")
                    },
                    ...logsArr,
                    ...moduleInfoArr,
                    ...jobInfoArr,
                    ...metricsArr,
                    {
                        label: 'Auth types',
                        icon: LockFilledMajor,
                        selected: page === "auth-types",
                        route: "/dashboard/settings/auth-types",
                        onClick: () => navigate("/dashboard/settings/auth-types")
                    },
                    {
                        label: 'Default payloads',
                        icon: VariantMajor,
                        selected: page === "default-payloads",
                        route: "/dashboard/settings/default-payloads",
                        onClick: () => navigate("/dashboard/settings/default-payloads")
                    },
                    {
                        label: 'Advanced traffic filters',
                        icon: AdjustMinor,
                        selected: page === "advanced-filters",
                        route: "/dashboard/settings/advanced-filters",
                        onClick: () => navigate("/dashboard/settings/advanced-filters")
                    },
                    ...(dashboardCategory === CATEGORY_ENDPOINT_SECURITY ? [{
                        label: 'Proxy Patterns',
                        icon: CodeMajor,
                        selected: page === "proxy-patterns",
                        route: "/dashboard/settings/proxy-patterns",
                        onClick: () => navigate("/dashboard/settings/proxy-patterns")
                    },
                    {
                        label: 'Allowed Hosts',
                        icon: GlobeMajor,
                        selected: page === "allowed-hosts",
                        route: "/dashboard/settings/allowed-hosts",
                        onClick: () => navigate("/dashboard/settings/allowed-hosts")
                    },
                    ...(window.USER_NAME?.toLowerCase()?.endsWith("@akto.io") ? [{
                        label: 'Installer Version Control',
                        icon: LockFilledMajor,
                        selected: page === "endpoint-shield",
                        route: "/dashboard/settings/endpoint-shield",
                        onClick: () => navigate("/dashboard/settings/endpoint-shield")
                    },
                    {
                        label: 'Remote Commands',
                        icon: AutomationFilledMajor,
                        selected: page === "remote-commands" || path.includes("/settings/remote-commands/"),
                        route: "/dashboard/settings/remote-commands",
                        onClick: () => navigate("/dashboard/settings/remote-commands")
                    }] : []),
                    {
                        label: 'File Inspection',
                        icon: CodeMajor,
                        selected: page === "file-inspection",
                        route: "/dashboard/settings/file-inspection",
                        onClick: () => navigate("/dashboard/settings/file-inspection")
                    }
                ] : []),
                    {
                        label: 'Test library',
                        icon: VocabularyMajor,
                        selected: page === "test-library",
                        route: "/dashboard/settings/test-library",
                        onClick: () => navigate("/dashboard/settings/test-library")
                    },
                    ...billingArr,
                    ...selfHostedArr,
                    ...auditLogsArr,
                    {
                        label: 'Help & Support',
                        icon: ChatMajor,
                        selected: page === "help",
                        route: "/dashboard/settings/help",
                        onClick: () => navigate("/dashboard/settings/help")
                    }
                ])}
            />
        </Navigation>
    )
}

export default SettingsLeftNav