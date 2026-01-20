import {Box, Navigation, Text} from "@shopify/polaris";
import {
    AppsFilledMajor,
    InventoryFilledMajor,
    MarketingFilledMinor,
    ReportFilledMinor,
    DiamondAlertMinor,
    FinancesMinor,
    LockMajor,
    AutomationFilledMajor,
    MagicMinor
} from "@shopify/polaris-icons";
import {useLocation, useNavigate} from "react-router-dom";

import "./LeftNav.css";
import PersistStore from "../../../../main/PersistStore";
import LocalStore from "../../../../main/LocalStorageStore";
import Store from "../../../store";
import api from "../../../../signup/api";
import { useMemo, useState} from "react";
import func from "@/util/func";
import Dropdown from "../Dropdown";
import SessionStore from "../../../../main/SessionStore";
import IssuesStore from "../../../pages/issues/issuesStore";
import { CATEGORY_AGENTIC_SECURITY, CATEGORY_API_SECURITY, CATEGORY_ENDPOINT_SECURITY, CATEGORY_DAST, mapLabel } from "../../../../main/labelHelper";

export default function LeftNav() {
    const navigate = useNavigate();
    const location = useLocation();
    const currPathString = func.transformString(location.pathname);

    const [leftNavSelected, setLeftNavSelected] = useState(currPathString);

    const active = PersistStore((state) => state.active);
    const setActive = PersistStore((state) => state.setActive);
    const accounts = Store(state => state.accounts) || {};
    const activeAccount = Store(state => state.activeAccount);
    const resetAll = PersistStore(state => state.resetAll);
    const resetStore = LocalStore(state => state.resetStore);
    const resetSession = SessionStore(state => state.resetStore);
    const resetFields = IssuesStore(state => state.resetStore);

    const handleSelect = (selectedId) => {
        setLeftNavSelected(selectedId);
    };

    const handleAccountChange = async (selected) => {
        resetAll();
        resetStore();
        resetSession();
        resetFields();
        await api.goToAccount(selected);
        func.setToast(true, false, `Switched to account ${accounts[selected]}`);
        window.location.href = '/dashboard/observe/inventory';
    };

    const accountOptions = Object.keys(accounts).map(accountId => ({
        label: accounts[accountId],
        value: accountId
    }));

    let reportsSubNavigationItems = [
        {
            label: "Issues",
            onClick: () => {
                navigate("/dashboard/reports/issues");
                handleSelect("dashboard_reports_issues");
                setActive("active");
            },
            selected: leftNavSelected === "dashboard_reports_issues",
        }
    ]

    if (window.USER_NAME.indexOf("@akto.io")) {
        reportsSubNavigationItems.push({
            label: "Compliance",
            onClick: () => {
                navigate("/dashboard/reports/compliance");
                handleSelect("dashboard_reports_compliance");
                setActive("active");
            },
            selected: leftNavSelected === "dashboard_reports_compliance",
        })
        reportsSubNavigationItems.push({
            label: "Threat",
            onClick: () => {
                navigate("/dashboard/reports/threat");
                handleSelect("dashboard_reports_tthreat");
                setActive("active");
            },
            selected: leftNavSelected === "dashboard_reports_tthreat",
        })
    }

    const dashboardCategory = PersistStore((state) => state.dashboardCategory) || "API Security";

    // Allowed users for Dashboard access
    const allowedDashboardUsers = [
        "ankush@akto.io",
        "shivam@akto.io",
        "umesh@akto.io",
        "shivansh@akto.io",
        "aryan@akto.io",
        "fenil@akto.io"
    ];
    const isAllowedDashboardUser = window.USER_NAME && allowedDashboardUsers.includes(window.USER_NAME.toLowerCase());

    const navItems = useMemo(() => {
        let items = [
            {
                label: (!func.checkLocal()) ? (
                    <Box paddingBlockEnd={"2"}>
                        <Dropdown
                            id={`select-account`}
                            menuItems={accountOptions}
                            initial={() => accounts[activeAccount]}
                            selected={(type) => handleAccountChange(type)}
                        />
                    </Box>

                ) : null
            },
            ...(isAllowedDashboardUser && dashboardCategory === CATEGORY_AGENTIC_SECURITY ? [{
                label: "Dashboard",
                icon: ReportFilledMinor,
                onClick: () => {
                    handleSelect("dashboard_agentic_dashboard");
                    navigate("/dashboard/agentic-dashboard");
                    setActive("normal");
                },
                selected: leftNavSelected === "dashboard_agentic_dashboard",
                key: "1",
            }] : isAllowedDashboardUser && dashboardCategory === CATEGORY_API_SECURITY ? [{
                label: "Dashboard",
                icon: ReportFilledMinor,
                onClick: () => {
                    handleSelect("dashboard_api_dashboard");
                    navigate("/dashboard/view");
                    setActive("normal");
                }
            }] : isAllowedDashboardUser && dashboardCategory === CATEGORY_ENDPOINT_SECURITY ? [{
                label: "Dashboard",
                icon: ReportFilledMinor,
                onClick: () => {
                    handleSelect("dashboard_endpoint_security_dashboard");
                    navigate("/dashboard/endpoint-dashboard");
                    setActive("normal");
                }
            }] : []),
            ...(dashboardCategory !== "Endpoint Security" ? [{
                label: mapLabel("API Security Posture", dashboardCategory),
                icon: ReportFilledMinor,
                onClick: () => {
                    handleSelect("dashboard_home");
                    navigate("/dashboard/home");
                    setActive("normal");
                },
                selected: leftNavSelected === "dashboard_home",
                key: "2",
            }] : []),
            {
                url: "#",
                label: (
                    <Text
                        variant="bodyMd"
                        fontWeight="medium"
                        color={
                            leftNavSelected.includes("observe")
                                ? active === "active"
                                    ? "subdued"
                                    : ""
                                : ""
                        }
                    >
                        {mapLabel("API Discovery", dashboardCategory)}
                    </Text>
                ),
                icon: InventoryFilledMajor,
                onClick: () => {
                    const targetPath = dashboardCategory === CATEGORY_ENDPOINT_SECURITY
                        ? "/dashboard/observe/agentic-assets"
                        : "/dashboard/observe/inventory";
                    const targetHandle = dashboardCategory === CATEGORY_ENDPOINT_SECURITY
                        ? "dashboard_observe_agentic_assets"
                        : "dashboard_observe_inventory";
                    handleSelect(targetHandle);
                    navigate(targetPath);
                    setActive("normal");
                },
                selected: leftNavSelected.includes("_observe"),
                subNavigationItems: [
                    ...(dashboardCategory === CATEGORY_ENDPOINT_SECURITY ? [{
                        label: "Agentic assets",
                        onClick: () => {
                            navigate("/dashboard/observe/agentic-assets");
                            handleSelect("dashboard_observe_agentic_assets");
                            setActive("active");
                        },
                        selected: leftNavSelected === "dashboard_observe_agentic_assets",
                    }] : [{
                        label: "Collections",
                        onClick: () => {
                            navigate("/dashboard/observe/inventory");
                            handleSelect("dashboard_observe_inventory");
                            setActive("active");
                        },
                        selected: leftNavSelected === "dashboard_observe_inventory",
                    }]),
                    ...(!(func.isDemoAccount() && (dashboardCategory === "Agentic Security" || dashboardCategory === "Endpoint Security")) ? [
                    {
                        label: "Recent Changes",
                        onClick: () => {
                            navigate("/dashboard/observe/changes");
                            handleSelect("dashboard_observe_changes");
                            setActive("active");
                        },
                        selected: leftNavSelected === "dashboard_observe_changes",
                    }] : []),
                    {
                        label: "Sensitive Data",
                        onClick: () => {
                            navigate("/dashboard/observe/sensitive");
                            handleSelect("dashboard_observe_sensitive");
                            setActive("active");
                        },
                        selected: leftNavSelected === "dashboard_observe_sensitive",
                    },
                    ...(window?.STIGG_FEATURE_WISE_ALLOWED?.AKTO_DAST?.isGranted && dashboardCategory === CATEGORY_DAST ? [{
                        label: "DAST scans",
                        onClick: () => {
                            navigate("/dashboard/observe/dast-progress");
                            handleSelect("dashboard_observe_dast_progress");
                            setActive("active");
                        },
                        selected: leftNavSelected === "dashboard_observe_dast_progress"
                    }] : []),
                    ...((dashboardCategory === "MCP Security" || dashboardCategory === "Agentic Security" || dashboardCategory === "Endpoint Security") ? [{
                        label: "Audit Data",
                        onClick: () => {
                            navigate("/dashboard/observe/audit");
                            handleSelect("dashboard_observe_audit");
                            setActive("active");
                        },
                        selected: leftNavSelected === "dashboard_observe_audit",
                    }] : []),
                    ...(dashboardCategory === CATEGORY_ENDPOINT_SECURITY ? [{
                        label: "Endpoint Shield",
                        onClick: () => {
                            navigate("/dashboard/observe/endpoint-shield");
                            handleSelect("dashboard_observe_endpoint_shield");
                            setActive("active");
                        },
                        selected: leftNavSelected === "dashboard_observe_endpoint_shield",
                    }] : []),
                ],
                key: "3",
            },
            ...(dashboardCategory !== "Endpoint Security" ? [{
                url: "#",
                label: (
                    <Text
                        variant="bodyMd"
                        fontWeight="medium"
                        color={
                            leftNavSelected.includes("testing")
                                ? active === "active"
                                    ? "subdued"
                                    : ""
                                : ""
                        }
                    >
                        {mapLabel("API Testing", dashboardCategory)}
                    </Text>
                ),
                icon: MarketingFilledMinor,
                onClick: () => {
                    navigate("/dashboard/testing/");
                    handleSelect("dashboard_testing");
                    setActive("normal");
                },
                selected: leftNavSelected.includes("_testing"),
                subNavigationItems: [
                    {
                        label: "Results",
                        onClick: () => {
                            navigate("/dashboard/testing/");
                            handleSelect("dashboard_testing");
                            setActive("active");
                        },
                        selected: leftNavSelected === "dashboard_testing",
                    },
                    {
                        label: mapLabel("Test", dashboardCategory) + " Roles",
                        onClick: () => {
                            navigate("/dashboard/testing/roles");
                            handleSelect("dashboard_testing_roles");
                            setActive("active");
                        },
                        selected: leftNavSelected === "dashboard_testing_roles",
                    },
                    {
                        label: "User Config",
                        onClick: () => {
                            navigate("/dashboard/testing/user-config");
                            handleSelect("dashboard_testing_user_config");
                            setActive("active");
                        },
                        selected: leftNavSelected === "dashboard_testing_user_config",
                    },
                    {
                        label:mapLabel("Test", dashboardCategory) + " Suite",
                        onClick:()=>{
                            navigate("/dashboard/testing/test-suite");
                            handleSelect("dashboard_testing_test_suite");
                            setActive("active");
                        },
                        selected: leftNavSelected === "dashboard_testing_test_suite",
                    }
                ],
                key: "4",
            }] : []),
            ...(dashboardCategory !== "Endpoint Security" ? [{
                url: "#",
                label: (
                    <Text variant="bodyMd" fontWeight="medium">
                        {mapLabel("Test library", dashboardCategory)}
                    </Text>
                ),
                icon: FinancesMinor,
                onClick: () => {
                    handleSelect("dashboard_test_library_tests");
                    navigate("/dashboard/test-library/tests");
                    setActive("normal");
                },
                selected: leftNavSelected.includes("_test_library"),
                subNavigationItems: [
                    {
                        label: mapLabel("More Tests", dashboardCategory),
                        onClick: () => {
                            navigate("/dashboard/test-library/tests");
                            handleSelect("dashboard_test_library_tests");
                            setActive("active");
                        },
                        selected: leftNavSelected === "dashboard_test_library_tests",
                    },
                    {
                        label: "Editor",
                        onClick: () => {
                            navigate("/dashboard/test-editor");
                            handleSelect("dashboard_test_library_test_editor");
                            setActive("active");
                        },
                        selected: leftNavSelected === "dashboard_test_library_test_editor",
                    },
                ],
                key: "5",
            }] : []),
            ...(dashboardCategory === "Agentic Security" && func.isDemoAccount() ? [{
                label: (
                    <Text variant="bodyMd" fontWeight="medium">
                        Prompt Hardening
                    </Text>
                ),
                icon: AutomationFilledMajor,
                onClick: () => {
                    handleSelect("dashboard_prompt_hardening");
                    navigate("/dashboard/prompt-hardening");
                    setActive("normal");
                },
                selected: leftNavSelected === "dashboard_prompt_hardening",
                key: "prompt_hardening",
            }] : []),
            {
                url: "#",
                label: (
                    <Text
                        variant="bodyMd"
                        fontWeight="medium"
                        color={
                            leftNavSelected.includes("reports")
                                ? active === "active"
                                    ? "subdued"
                                    : ""
                                : ""
                        }
                    >
                        Reports
                    </Text>
                ),
                icon: ReportFilledMinor,
                onClick: () => {
                    const targetPath = dashboardCategory === CATEGORY_ENDPOINT_SECURITY
                        ? "/dashboard/reports/threat"
                        : "/dashboard/reports/issues";
                    const targetHandle = dashboardCategory === CATEGORY_ENDPOINT_SECURITY
                        ? "dashboard_reports_threat"
                        : "dashboard_reports_issues";
                    navigate(targetPath);
                    handleSelect(targetHandle);
                    setActive("normal");
                },
                selected: leftNavSelected.includes("_reports"),
                subNavigationItems: dashboardCategory === CATEGORY_ENDPOINT_SECURITY
                    ? [{
                        label: "Threat",
                        onClick: () => {
                            navigate("/dashboard/reports/threat");
                            handleSelect("dashboard_reports_threat");
                            setActive("active");
                        },
                        selected: leftNavSelected === "dashboard_reports_threat",
                    }]
                    : reportsSubNavigationItems,
                key: "6",
            },
            ...(window?.STIGG_FEATURE_WISE_ALLOWED?.THREAT_DETECTION?.isGranted && dashboardCategory !== CATEGORY_DAST  ?  [{
                    label: (
                        <Text variant="bodyMd" fontWeight="medium">
                            {mapLabel("Threat Detection", dashboardCategory)}
                        </Text>
                    ),
                    icon: DiamondAlertMinor,
                    onClick: () => {
                        handleSelect("dashboard_threat_actor");
                        navigate("/dashboard/protection/threat-actor");
                        setActive("normal");
                    },
                    selected: leftNavSelected.includes("_threat") ||  leftNavSelected.includes("_guardrails"),
                    url: "#",
                    key: "7",
                    subNavigationItems: [
                        ...((dashboardCategory === "API Security" || dashboardCategory === "Endpoint Security") ? [{
                            label: "Dashboard",
                            onClick: () => {
                                navigate("/dashboard/protection/threat-dashboard");
                                handleSelect("dashboard_threat_dashboard");
                                setActive("active");
                            },
                            selected: leftNavSelected === "dashboard_threat_dashboard",
                        }] : []),
                        {
                            label: `${mapLabel("Threat", dashboardCategory)} Actors`,
                            onClick: () => {
                                navigate("/dashboard/protection/threat-actor");
                                handleSelect("dashboard_threat_actor");
                                setActive("active");
                            },
                            selected: leftNavSelected === "dashboard_threat_actor",
                        },
                        {
                            label: `${mapLabel("Threat", dashboardCategory)} Activity`,
                            onClick: () => {
                                navigate("/dashboard/protection/threat-activity");
                                handleSelect("dashboard_threat_activity");
                                setActive("active");
                            },
                            selected:
                                leftNavSelected === "dashboard_threat_activity",
                        },
                        {
                            label: `${mapLabel("APIs", dashboardCategory)} Under ${mapLabel("Threat", dashboardCategory)}`,
                            onClick: () => {
                                navigate("/dashboard/protection/threat-api");
                                handleSelect("dashboard_threat_api");
                                setActive("active");
                            },
                            selected:
                                leftNavSelected === "dashboard_threat_api",
                        },
                        ...((dashboardCategory === "Agentic Security" || dashboardCategory === "Endpoint Security") ? [{
                            label: "Guardrail Policies",
                            onClick: () => {
                                navigate("/dashboard/guardrails/policies");
                                handleSelect("dashboard_guardrails_policies");
                                setActive("active");
                            },
                            selected: leftNavSelected === "dashboard_guardrails_policies",
                            }] : []),
                        ...(dashboardCategory !== "Endpoint Security" ? [{
                            label: "Threat Policies",
                            onClick: () => {
                                navigate("/dashboard/protection/threat-policy");
                                handleSelect("dashboard_threat_policy");
                                setActive("active");
                            },
                            selected:
                                leftNavSelected === "dashboard_threat_policy",
                        }] : [])
                    ],
                }] : []),
            // ...(window?.STIGG_FEATURE_WISE_ALLOWED?.AI_AGENTS?.isGranted && dashboardCategory === "API Security" ? [{
            //     label: (
            //         <Text variant="bodyMd" fontWeight="medium">
            //             AI Agents
            //         </Text>
            //     ),
            //     icon: StarFilledMinor,
            //     onClick: () => {
            //         handleSelect("agent_team_members");
            //         navigate("/dashboard/agent-team/members");
            //         setActive("normal");
            //     },
            //     selected: leftNavSelected.includes("agent_team"),
            //     url: "#",
            //     key: "8",
            // }] : []),
            ...(dashboardCategory === "MCP Security" ? [{
                label: (
                    <Text variant="bodyMd" fontWeight="medium">
                        MCP Guardrails
                    </Text>
                ),
                icon: LockMajor,
                onClick: () => {
                    handleSelect("dashboard_mcp_guardrails");
                    navigate("/dashboard/guardrails/activity");
                    setActive("normal");
                },
                selected: leftNavSelected.includes("_guardrails"),
                url: "#",
                key: "9",
                subNavigationItems: [
                    {
                        label: "Guardrails Activity",
                        onClick: () => {
                            navigate("/dashboard/guardrails/activity");
                            handleSelect("dashboard_guardrails_activity");
                            setActive("active");
                        },
                        selected: leftNavSelected === "dashboard_guardrails_activity",
                    },
                    {
                        label: "Guardrails Policies",
                        onClick: () => {
                            navigate("/dashboard/guardrails/policies");
                            handleSelect("dashboard_guardrails_policies");
                            setActive("active");
                        },
                        selected:
                            leftNavSelected === "dashboard_guardrails_policies",
                    }
                ]

            }] : []),
            ...(dashboardCategory === "Gen AI" ? [{
                label: (
                    <Text variant="bodyMd" fontWeight="medium">
                        AI Agent Guardrails
                    </Text>
                ),
                icon: LockMajor,
                onClick: () => {
                    handleSelect("dashboard_ai_agent_guardrails");
                    navigate("/dashboard/guardrails/activity");
                    setActive("normal");
                },
                selected: leftNavSelected.includes("_guardrails"),
                url: "#",
                key: "10",
                subNavigationItems: [
                    {
                        label: "Guardrails Activity",
                        onClick: () => {
                            navigate("/dashboard/guardrails/activity");
                            handleSelect("dashboard_guardrails_activity");
                            setActive("active");
                        },
                        selected: leftNavSelected === "dashboard_guardrails_activity",
                    },
                    {
                        label: "Guardrails Policies",
                        onClick: () => {
                            navigate("/dashboard/guardrails/policies");
                            handleSelect("dashboard_guardrails_policies");
                            setActive("active");
                        },
                        selected:
                            leftNavSelected === "dashboard_guardrails_policies",
                    }
                ]
            }] : [])
        ]

        // Add Ask AI navigation item
        const askAiExists = items.find(item => item.key === "ask_ai")
        if (!askAiExists && window.USER_NAME.indexOf("@akto.io")) {
            items.splice(1, 0, {
                label: "Ask Akto",
                icon: MagicMinor,
                onClick: () => {
                    handleSelect("dashboard_ask_ai")
                    navigate("/dashboard/ask-ai")
                    setActive("normal")
                },
                selected: leftNavSelected === "dashboard_ask_ai",
                key: "ask_ai",
            })
        }

        // Add Quick Start navigation item
        const exists = items.find(item => item.key === "quick_start")
        if (!exists) {
            // Find the correct position to insert "Quick Start"
            // It should be inserted after Dashboard (if present) or after the first navigation item
            // For akto users: after Dashboard (index 2)
            // For non-akto users: after "API Security Posture" (if present) or after "API Discovery"
            let insertIndex = 1; // Default: after Account dropdown
            
            // If Dashboard is present (akto users), insert after Dashboard (index 2)
            if (isAllowedDashboardUser && (
                dashboardCategory === CATEGORY_AGENTIC_SECURITY ||
                dashboardCategory === CATEGORY_API_SECURITY ||
                dashboardCategory === CATEGORY_ENDPOINT_SECURITY
            )) {
                insertIndex = 2;
            }
            
            items.splice(insertIndex, 0, {
                label: mapLabel("Quick Start", dashboardCategory),
                icon: AppsFilledMajor,
                onClick: () => {
                    handleSelect("dashboard_quick_start")
                    navigate("/dashboard/quick-start")
                    setActive("normal")
                },
                selected: leftNavSelected === "dashboard_quick_start",
                key: "quick_start",
            })
        }

        return items
    }, [dashboardCategory, leftNavSelected])

    const navigationMarkup = (
        <div className={`${active} ${dashboardCategory === "Agentic Security" ? "agentic-security-nav" : ""}`}>
            <Navigation location="/">
                <Navigation.Section
                    items={navItems}
                />
            </Navigation>
        </div>
    );

    return navigationMarkup;
}