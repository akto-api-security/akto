import {Box, Navigation, Text} from "@shopify/polaris";
import {
    AppsFilledMajor,
    InventoryFilledMajor,
    MarketingFilledMinor,
    ReportFilledMinor,
    DiamondAlertMinor,
    FinancesMinor,
    LockMajor,
    AutomationFilledMajor
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
import { CATEGORY_DAST, mapLabel } from "../../../../main/labelHelper";

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
    }

    const dashboardCategory = PersistStore((state) => state.dashboardCategory) || "API Security";

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
            {
                label: mapLabel("API Security Posture", dashboardCategory),
                icon: ReportFilledMinor,
                onClick: () => {
                    handleSelect("dashboard_home");
                    navigate("/dashboard/home");
                    setActive("normal");
                },
                selected: leftNavSelected === "dashboard_home",
                key: "2",
            },
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
                    handleSelect("dashboard_observe_inventory");
                    navigate("/dashboard/observe/inventory");
                    setActive("normal");
                },
                selected: leftNavSelected.includes("_observe"),
                subNavigationItems: [
                    {
                        label: "Collections",
                        onClick: () => {
                            navigate("/dashboard/observe/inventory");
                            handleSelect("dashboard_observe_inventory");
                            setActive("active");
                        },
                        selected: leftNavSelected === "dashboard_observe_inventory",
                    },
                    {
                        label: "Recent Changes",
                        onClick: () => {
                            navigate("/dashboard/observe/changes");
                            handleSelect("dashboard_observe_changes");
                            setActive("active");
                        },
                        selected: leftNavSelected === "dashboard_observe_changes",
                    },
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
                    ...((dashboardCategory === "MCP Security" || dashboardCategory === "Agentic Security") ? [{
                        label: "Audit Data",
                        onClick: () => {
                            navigate("/dashboard/observe/audit");
                            handleSelect("dashboard_observe_audit");
                            setActive("active");
                        },
                        selected: leftNavSelected === "dashboard_observe_audit",
                    }] : []),
                    ...((dashboardCategory === "MCP Security" || dashboardCategory === "Agentic Security") ? [{
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
            {
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
            },
            {
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
            },
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
                    navigate("/dashboard/reports/issues");
                    handleSelect("dashboard_reports_issues");
                    setActive("normal");
                },
                selected: leftNavSelected.includes("_reports"),
                subNavigationItems: reportsSubNavigationItems,
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
                        ...(dashboardCategory === "API Security" ? [{
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
                        ...(dashboardCategory === "Agentic Security" ? [{
                            label: "Guardrail Policies",
                            onClick: () => {
                                navigate("/dashboard/guardrails/policies");
                                handleSelect("dashboard_guardrails_policies");
                                setActive("active");
                            },
                            selected: leftNavSelected === "dashboard_guardrails_policies",
                            }] : []),
                        {
                            label: "Threat Policies",
                            onClick: () => {
                                navigate("/dashboard/protection/threat-policy");
                                handleSelect("dashboard_threat_policy");
                                setActive("active");
                            },
                            selected:
                                leftNavSelected === "dashboard_threat_policy",
                        }
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

        const exists = items.find(item => item.key === "quick_start")
        if (!exists) {
            items.splice(1, 0, {
                label: "Quick Start",
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