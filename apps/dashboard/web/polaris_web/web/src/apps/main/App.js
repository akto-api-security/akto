import HomePage from "../dashboard/pages/home/HomePage"
import TestRunsPage from "../dashboard/pages/testing/TestRunsPage/TestRunsPage";
import SingleTestRunPage from "../dashboard/pages/testing/SingleTestRunPage/SingleTestRunPage"
import AllSensitiveData from "../dashboard/pages/observe/AllSensitiveData/AllSensitiveData";
import ApiCollections from "../dashboard/pages/observe/api_collections/ApiCollections";
import ApiQuery from "../dashboard/pages/observe/api_collections/APIQuery";
import DebugEndpointsMode from "../dashboard/pages/observe/api_collections/DebugEndpointsMode";
import ApiEndpoints from "../dashboard/pages/observe/api_collections/ApiEndpoints";
import SensitiveDataExposure from "../dashboard/pages/observe/SensitiveDataExposure/SensitiveDataExposure";
import SingleRequest from "../dashboard/pages/observe/SingleRequest/SingleRequest";
import PageObserve from "../dashboard/pages/observe/PageObserve"
import PageTesting from "../dashboard/pages/testing/PageTesting";
import {
    createBrowserRouter,
    RouterProvider,
    Navigate,
} from "react-router-dom";
import BurpSuite from "../dashboard/pages/settings/integrations/BurpSuite";
import Integrations from "../dashboard/pages/settings/integrations/Integrations";
import Settings from "../dashboard/pages/settings/Settings";
import Users from "../dashboard/pages/settings/users/Users";
import Roles from "../dashboard/pages/settings/roles/Roles";
import Postman from "../dashboard/pages/settings/integrations/Postman";
import Jira from "../dashboard/pages/settings/integrations/Jira";
import ApiTokens from "../dashboard/pages/settings/integrations/ApiTokens";
import AktoGPT from "../dashboard/pages/settings/integrations/AktoGPT";
import GithubSso from "../dashboard/pages/settings/integrations/GithubSso";
import GithubAppIntegration from "../dashboard/pages/settings/integrations/GithubAppIntegration";
import HealthLogs from "../dashboard/pages/settings/health_logs/HealthLogs";
import About from "../dashboard/pages/settings/about/About";
import ThreatConfiguration from "../dashboard/pages/settings/threat_configuration/ThreatConfiguration";
import Metrics from "../dashboard/pages/settings/metrics/Metrics";
import TestEditor from "../dashboard/pages/test_editor/TestEditor";
import PromptHardening from "../dashboard/pages/prompt_hardening/PromptHardening";
import DataTypes from "../dashboard/pages/observe/data_types/DataTypes";
import IssuesPage from "../dashboard/pages/issues/IssuesPage/IssuesPage";
import CompliancePage from "../dashboard/pages/issues/IssuesPage/CompliancePage";
import QuickStart from "../dashboard/pages/quick_start/QuickStart";
import AgentTeam from "../dashboard/pages/agent_team/AgentTeam";
import Webhooks from "../dashboard/pages/settings/integrations/webhooks/Webhooks";
import Webhook from "../dashboard/pages/settings/integrations/webhooks/Webhook";
import TestRolesPage from "../dashboard/pages/testing/TestRolesPage/TestRolesPage";
import TestRoleSettings from "../dashboard/pages/testing/TestRoleSettings/TestRoleSettings";
import UserConfig from "../dashboard/pages/testing/user_config/UserConfig";
import AuthTypes from "../dashboard/pages/settings/auth_types/AuthTypes";
import DefaultPayloads from "../dashboard/pages/settings/default_payloads/DefaultPayloads";
import AuthTypeDetails from "../dashboard/pages/settings/auth_types/AuthTypeDetails";
import Tags from "../dashboard/pages/settings/tags/Tags";
import Billing from "../dashboard/pages/settings/billing/Billing";
import SelfHosted from "../dashboard/pages/settings/billing/SelfHosted";
import TagDetails from "../dashboard/pages/settings/tags/TagDetails";
import Onboarding from "../dashboard/pages/onboarding/Onboarding";
import Dashboard from "../dashboard/pages/Dashboard";
import Slack from "../dashboard/pages/settings/integrations/Slack";
import ApiChanges from "../dashboard/pages/observe/api_collections/ApiChanges";

import Store from "../dashboard/store";
import {generateSearchData} from "@/util/searchItems"
import {useEffect, useMemo} from "react";
import func from "@/util/func";
import CICD from "../dashboard/pages/settings/integrations/CICD";
import ErrorComponent from "../dashboard/components/shared/ErrorComponent";
import OktaIntegration from "../dashboard/pages/settings/integrations/OktaIntegration";
import AzureSso from "../dashboard/pages/settings/integrations/sso/AzureSso";

import HomeDashboard from "../dashboard/pages/dashboard/HomeDashboard";
import TestLibrary from "../dashboard/pages/settings/test_library/TestLibrary";
import {useStiggContext} from '@stigg/react-sdk';
import DependencyTable from "../dashboard/pages/testing/DependencyTable/DependencyTable";
import TestRoleAccessMatrix from "../dashboard/pages/testing/TestRoleAccessMatrix/TestRoleAccessMatrix";
import SignupPage from "../signup/pages/SignupPage";
import PageCheckInbox from "../signup/pages/PageCheckInbox"
import PageBusinessEmail from "../signup/pages/PageBusinessEmail"
import TokenValidator from "./TokenValidator"
import {TableContextProvider} from "@/apps/dashboard/components/tables/TableContext";
import VulnerabilityReport from "../dashboard/pages/testing/vulnerability_report/VulnerabilityReport";
import ThreatDetectionPage from "../dashboard/pages/threat_detection/ThreatDetectionPage";

import {PollingProvider} from "./PollingProvider";
import Help from "../dashboard/pages/settings/help_and_support/Help";
import AdvancedTrafficFilters from "../dashboard/pages/settings/traffic-conditions/AdvancedTrafficFilters";
import GoogleSamlSso from "../dashboard/pages/settings/integrations/sso/GoogleSamlSso";
import SignUpWithSSO from "../signup/components/SignUpWithSSO";

import TeamsWebhooks from "../dashboard/pages/settings/integrations/teamsWebhooks/TeamsWebhooks";
import TeamsWebhook from "../dashboard/pages/settings/integrations/teamsWebhooks/TeamsWebhook";
import AuditLogs from "../dashboard/pages/settings/audit_logs/AuditLogs";
import ThreatApiPage from "../dashboard/pages/threat_detection/ThreatApiPage";
import ThreatActorPage from "../dashboard/pages/threat_detection/ThreatActorPage";
import ThreatPolicyPage from "../dashboard/pages/threat_detection/ThreatPolicyPage";
import ConfigureExploitsPage from "../dashboard/pages/threat_detection/ConfigureExploitsPage";
import ConfigureIgnoredEventsPage from "../dashboard/pages/threat_detection/ConfigureIgnoredEventsPage";
import TestSuite from "../dashboard/pages/testing/testSuite/TestSuite";
import TestsTablePage from "../dashboard/pages/test_editor/tests_table/TestsTablePage";
import Splunk from "../dashboard/pages/settings/integrations/Splunk";
import F5Waf from "../dashboard/pages/settings/integrations/F5Waf";
import AWSWaf from "../dashboard/pages/settings/integrations/AWSWaf";
import AgentConfig from "../dashboard/pages/settings/integrations/AgentConfig";
import AzureBoards from "../dashboard/pages/settings/integrations/AzureBoards";
import ServiceNow from "../dashboard/pages/settings/integrations/ServiceNow";
import McpRegistry from "../dashboard/pages/settings/integrations/McpRegistry";
import CloudflareWaf from "../dashboard/pages/settings/integrations/CloudflareWaf";
import UndoDemergedApis from "../dashboard/pages/settings/undo_demerged_apis/UndoDemergedApis";
import GmailWebhookCore from "../dashboard/pages/settings/integrations/gmailWebhooks/GmailWebhookCore";
import GmailWebhook from "../dashboard/pages/settings/integrations/gmailWebhooks/GmailWebhook";
import McpSecurityPage from "../dashboard/pages/mcp-security/McpSecurityPage.jsx";
import AuditData from "../dashboard/pages/observe/AuditData";
import EndpointShieldMetadata from "../dashboard/pages/observe/EndpointShieldMetadata";
import GuardrailDetection    from "../dashboard/pages/guardrails/GuardrailDetection";
import GuardrailDetectionDemo from "../dashboard/pages/guardrails/GuardrailDetectionDemo";
import GuardrailPolicies   from "../dashboard/pages/guardrails/GuardrailPolicies";
import ThreatDashboardPage from "../dashboard/pages/threat_detection/ThreatDashboardPage";
import OpenApiAgentTester from "../dashboard/pages/observe/OpenApiAgentTester";
import DastProgress from "../dashboard/pages/observe/api_collections/DastProgress.jsx";
import DastProgressSingle from "../dashboard/pages/observe/api_collections/DastProgressSingle.jsx";

// if you add a component in a new path, please verify the search implementation in function -> 'getSearchItemsArr' in func.js

const router = createBrowserRouter([
    {
        path: "/dashboard",
        element: <Dashboard/>,
        children: [
            {
                path: "",
                element: <HomePage/>,
                children: [
                    {
                        path: "home",
                        element: <HomeDashboard/>,
                    },
                    {
                        path: "testing",
                        element: <PageTesting/>,
                        children: [
                            ...(["", "active", "cicd", "inactive"].map((i) => {
                                return {
                                    path: i,
                                    element: <TestRunsPage/>
                                }
                            })),
                            {
                                path: ":hexId",
                                element: <SingleTestRunPage/>
                            },
                            {
                                path: "roles",
                                element: <TestRolesPage/>
                            },
                            {
                                path: "roles/details",
                                element: <TestRoleSettings/>
                            },
                            {
                                path: "roles/access-matrix",
                                element: <TestRoleAccessMatrix/>
                            },
                            {
                                path: "user-config",
                                element: <UserConfig/>
                            },
                            {
                                path: "dependency",
                                element: <DependencyTable/>
                            },
                            {
                              path:"test-suite",
                              element:<TestSuite/>
                            }
                        ]
                    },
                    {
                        path: "observe",
                        element: <PageObserve/>,
                        children: [
                            {
                                path: "sensitive",
                                element: <AllSensitiveData/>
                            },
                            {
                                path: "inventory",
                                element: <ApiCollections/>
                            },
                            {
                                path: "query_mode",
                                element: <ApiQuery/>
                            },
                            {
                                path: "debug-endpoints",
                                element: <DebugEndpointsMode/>
                            },
                            {
                                path: "changes",
                                element: <ApiChanges/>
                            },
                            {
                                path: "inventory/:apiCollectionId",
                                element: <ApiEndpoints/>
                            },
                            {
                                path: "data-types",
                                element: <DataTypes/>
                            },
                            {
                                path: "sensitive/:subType",
                                element: <SensitiveDataExposure/>
                            },
                            {
                                path: "sensitive/:subType/:apiCollectionId/:urlAndMethod",
                                element: <SingleRequest/>
                            },
                            {
                                path: "audit",
                                element: <AuditData/>
                            },
                            {
                                path: "endpoint-shield",
                                element: <EndpointShieldMetadata/>
                            },
                            {
                                path: ":apiCollectionId/open-api-upload",
                                element: <OpenApiAgentTester/>
                            },
                            {
                                path: "dast-progress",
                                element: <DastProgress />
                            },
                            {
                                path: "dast-progress/:crawlId",
                                element: <DastProgressSingle />
                            },
                        ]
                    },
                    {
                        path:"test-library/tests",
                        element:<TestsTablePage/>
                    },
                    {
                        path: "issues",
                        element: <IssuesPage/>
                    },
                    {
                        path: "reports",
                        children: [
                            {
                                path: "issues",
                                element: <IssuesPage/>
                            },
                            {
                                path: "compliance",
                                element: <CompliancePage/>
                            }
                        ]
                    },
                    {
                        path: "protection",
                        children: [
                            {
                                path: "threat-dashboard",
                                element: <ThreatDashboardPage/>
                            },
                            {
                                path: "threat-activity",
                                element: <ThreatDetectionPage/>
                            },
                            {
                                path: "threat-api",
                                element: <ThreatApiPage/>
                            },
                            {
                                path: "threat-actor",
                                element: <ThreatActorPage/>
                            },
                            {
                                path: "threat-policy",
                                element: <ThreatPolicyPage/>
                            }
                            ,
                            {
                                path: "configure-exploits",
                                element: <ConfigureExploitsPage/>
                            }
                            ,
                            {
                                path: "configure-ignored-events",
                                element: <ConfigureIgnoredEventsPage/>
                            }
                        ]
                    },
                    {
                        path: "quick-start",
                        element: <QuickStart/>,
                    },
                    {
                        path: "agent-team",
                        children: [
                            {
                                path: "members",
                                element: <AgentTeam/>
                            }
                        ]
                    },
                    {
                        path: "mcp-security",
                        element: <McpSecurityPage/>,
                    },
                    {
                        path: "guardrails",
                        children: [
                            {
                                path: "activity",
                                element: func.isDemoAccount() ? <GuardrailDetectionDemo/> : <GuardrailDetection/>
                            },
                            {
                                path: "policies",
                                element: <GuardrailPolicies/>
                            }
                        ]
                    }
                ]
            },
            {
                path: "settings",
                element: <Settings/>,
                children: [
                    {
                        path: "users",
                        element: <Users/>
                    },
                    {
                        path: "roles",
                        element: <Roles/>
                    },
                    {
                        path: "threat-configuration",
                        element: <ThreatConfiguration/>
                    },
                    {
                        path: "Help",
                        element: <Help/>
                    },
                    {
                        path: "integrations",
                        element: <Integrations/>,
                    },
                    {
                        path: "about",
                        element: <About/>,
                    },
                    {
                        path: "metrics",
                        element: <Metrics/>,
                    },
                    {
                        path: "integrations/burp",
                        element: <BurpSuite/>,
                    },
                    {
                        path: "integrations/ci-cd",
                        element: <CICD/>,
                    },
                    {
                        path: "integrations/postman",
                        element: <Postman/>,
                    },
                    {
                        path: "integrations/splunk",
                        element: <Splunk/>,
                    },
                    {
                        path: "integrations/f5_waf",
                        element: <F5Waf/>,
                    },
                    {
                        path: "integrations/aws_waf",
                        element: <AWSWaf/>,
                    },
                    {
                        path: "integrations/cloudflare_waf",
                        element: <CloudflareWaf/>,
                    },
                    {
                        path: "integrations/agents",
                        element: <AgentConfig/>,
                    },
                    {
                        path: "integrations/jira",
                        element: <Jira/>,
                    },
                    {
                        path: "integrations/azure_boards",
                        element: <AzureBoards/>,
                    },
                    {
                        path: "integrations/servicenow",
                        element: <ServiceNow/>,
                    },
                    {
                        path: "integrations/akto_apis",
                        element: <ApiTokens/>,
                    },
                    {
                        path: "integrations/akto_gpt",
                        element: <AktoGPT/>,
                    },
                    {
                        path: "integrations/mcp_registry",
                        element: <McpRegistry/>,
                    },
                    {
                        path: "integrations/github_sso",
                        element: <GithubSso/>
                    },
                    {
                        path: "integrations/okta_sso",
                        element: <OktaIntegration/>
                    },
                    {
                        path: "integrations/azure_sso",
                        element: <AzureSso/>
                    },
                    {
                        path: "integrations/google_workspace_sso",
                        element: <GoogleSamlSso/>
                    },
                    {
                        path: "integrations/github_app",
                        element: <GithubAppIntegration/>
                    },
                    {
                        path: "integrations/slack",
                        element: <Slack/>,
                    },
                    {
                        path: "integrations/webhooks",
                        element: <Webhooks/>,
                    },
                    {
                        path: "integrations/webhooks/:webhookId",
                        element: <Webhook/>,
                    },
                    {
                        path: "integrations/webhooks/create_custom_webhook",
                        element: <Webhook/>,
                    },
                    {
                        path: "integrations/teamsWebhooks",
                        element: <TeamsWebhooks/>,
                    },
                    {
                        path: "integrations/teamsWebhooks/:webhookId",
                        element: <TeamsWebhook/>,
                    },
                    {
                        path: "integrations/teamsWebhooks/create_custom_webhook",
                        element: <TeamsWebhook/>,
                    },
                    {
                        path: "integrations/gmailWebhooks",
                        element: <GmailWebhook/>,
                    },
                    {
                        path: "integrations/gmailWebhooks/:webhookId",
                        element: <GmailWebhookCore/>,
                    },
                    {
                        path: "integrations/gmailWebhooks/create_custom_webhook",
                        element: <GmailWebhookCore/>,
                    },
                    {
                        path: "logs",
                        element: <HealthLogs/>,
                    },
                    {
                        path: "auth-types",
                        element: <AuthTypes/>
                    },
                    {
                        path: "default-payloads",
                        element: <DefaultPayloads/>
                    },
                    {
                        path: 'advanced-filters',
                        element: <AdvancedTrafficFilters/>
                    },
                    {
                        path: "auth-types/details",
                        element: <AuthTypeDetails/>
                    },
                    {
                        path: "test-library",
                        element: <TestLibrary/>
                    },
                    {
                        path: "billing",
                        element: <Billing/>
                    },
                    {
                        path: "self-hosted",
                        element: <SelfHosted/>
                    },
                    {
                        path: 'audit-logs',
                        element: <AuditLogs/>
                    },
                    {
                        path: 'undo-demerge-apis',
                        element: <UndoDemergedApis/>
                    }
                ]
            },
            {
                path: "test-editor/:testId",
                element: <TestEditor/>
            },
            {
                path: "test-editor",
                element: <TestEditor/>
            },
            {
                path: "prompt-hardening/:promptId",
                element: <PromptHardening/>
            },
            {
                path: "prompt-hardening",
                element: <PromptHardening/>
            },
            {
                path: "onboarding",
                element: <Onboarding/>
            },
            {
                path: "testing/summary/:reportId",
                element: <VulnerabilityReport/>
            },
            {
                path: "issues/summary/:reportId",
                element: <VulnerabilityReport/>
            }
        ],
        errorElement: <ErrorComponent/>
    },
    {
        path: "/login",
        element: <SignupPage/>,
    },
    {
        path: "/",
        element: <TokenValidator/>,
    },
    {
        path: "/signup",
        element: <SignupPage/>,
    },
    {
        path: "/check-inbox",
        element: <PageCheckInbox/>
    },
    {
        path: "/business-email",
        element: <PageBusinessEmail/>
    },
    {
        path: "/sso-login",
        element: <SignUpWithSSO/>
    },
    // catches all undefined paths and redirects to homepage.
    {
        path: "*",
        element: <Navigate to="/dashboard/home"/>,
    },
])

function App() {
    const setAllRoutes = Store(state => state.setAllRoutes)
    const searchData = generateSearchData(router.routes)
    const {stigg} = useStiggContext();
    useEffect(() => {
        stigg.setCustomerId(window.STIGG_CUSTOMER_ID, window.STIGG_CUSTOMER_TOKEN)

    })


    useEffect(() => {
        const script = document.createElement('script')
        const scriptText = document.createTextNode(`
    self.MonacoEnvironment = {
      getWorkerUrl: function (moduleId, label) {
          if (label === 'json') {
              return '/polaris_web/web/dist/json.worker.js';
          }
          return '/polaris_web/web/dist/editor.worker.js';
      }
      };
    `);
        setAllRoutes(searchData)
        script.appendChild(scriptText);
        document.body.appendChild(script)
    }, [])

    return useMemo(() => {return (
        <PollingProvider>
            <TableContextProvider>
                <RouterProvider router={router}/>
            </TableContextProvider>
        </PollingProvider>
    )}, [router]) ;
}

export default App;