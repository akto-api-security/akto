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
import { useMemo, useState, useEffect} from "react";
import func from "@/util/func";
import Dropdown from "../Dropdown";
import SessionStore from "../../../../main/SessionStore";
import IssuesStore from "../../../pages/issues/issuesStore";
import { CATEGORY_API_SECURITY, mapLabel } from "../../../../main/labelHelper";

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
    const dashboardCategory = PersistStore((state) => state.dashboardCategory) || "API Security";
    const subcategory = PersistStore((state) => state.subCategory);
    const setSubCategory = PersistStore((state) => state.setSubCategory);

    // Set initial selection based on current path and subcategory
    useEffect(() => {
        const pathString = func.transformString(location.pathname);
        const subCategoryIndex = subcategory === "Endpoint Security" ? "1" : "0";
        const keyWithIndex = pathString + subCategoryIndex;
        
        // Only update if the current selection doesn't match
        if (leftNavSelected !== keyWithIndex && leftNavSelected !== pathString) {
            setLeftNavSelected(keyWithIndex);
        }
    }, [subcategory, location.pathname]);

    const handleClick = (key, path, activeState, subcategoryValue = "Default") => {
        const finalKey = subcategoryValue === "Endpoint Security" ? key + "_1" : key + "_0";
        setLeftNavSelected(finalKey);
        navigate(path);
        setActive(activeState);
        if (subcategoryValue && subcategoryValue !== subcategory) {
            setSubCategory(subcategoryValue);
            // Clear collections cache to trigger refresh when subcategory changes
            PersistStore.getState().setAllCollections([]);
        }
    }

    const handleAccountChange = async (selected) => {
        resetAll();
        resetStore();
        resetSession();
        resetFields();
        await api.goToAccount(selected);
        func.setToast(true, false, `Switched to account ${accounts[selected]}`);
        window.location.href = '/dashboard/observe/inventory';
    }

    const accountOptions = Object.keys(accounts).map(accountId => ({
        label: accounts[accountId],
        value: accountId
    }));

    

    const getMainNavItems = (subCategoryValue) => {
        const subCategoryIndex = subCategoryValue === "Endpoint Security" ? "1" : "0";
        const isCurrentSubCategory = subcategory === subCategoryValue;
        
        let reportsSubNavigationItems = [
            {
                label: "Issues",
                onClick: () => handleClick("dashboard_reports_issues", "/dashboard/reports/issues", "active", subCategoryValue),
                selected: isCurrentSubCategory && (leftNavSelected === "dashboard_reports_issues" + subCategoryIndex || leftNavSelected === "dashboard_reports_issues"),
            }
        ]
    
        if (window.USER_NAME.indexOf("@akto.io")) {
            reportsSubNavigationItems.push({
                label: "Compliance",
                onClick: () => handleClick("dashboard_reports_compliance", "/dashboard/reports/compliance", "active", subCategoryValue),
                selected: isCurrentSubCategory && (leftNavSelected === "dashboard_reports_compliance" + subCategoryIndex || leftNavSelected === "dashboard_reports_compliance"),
            })
        }
        return [
            {
                label: "Quick Start",
                icon: AppsFilledMajor,
                onClick: () => handleClick("dashboard_quick_start", "/dashboard/quick-start", "normal", subCategoryValue),
                selected: isCurrentSubCategory && (leftNavSelected === "dashboard_quick_start" + subCategoryIndex || leftNavSelected === "dashboard_quick_start"),
                key: "quick_start",
            },
            {
                label: mapLabel("API Security Posture", dashboardCategory),
                icon: ReportFilledMinor,
                onClick: () => handleClick("dashboard_home", "/dashboard/home", "normal", subCategoryValue),
                selected: isCurrentSubCategory && (leftNavSelected === "dashboard_home" + subCategoryIndex || leftNavSelected === "dashboard_home"),
                key: "2",
            },
            {
                url: "#",
                label: (
                    <Text
                        variant="bodyMd"
                        fontWeight="medium"
                        color={
                            leftNavSelected.includes(subCategoryIndex) && leftNavSelected.includes("observe")
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
                onClick: () => handleClick("dashboard_observe_inventory", "/dashboard/observe/inventory", "normal", subCategoryValue),
                selected: isCurrentSubCategory && leftNavSelected.includes("_observe"),
                subNavigationItems: [
                    {
                        label: "Collections",
                        onClick: () => handleClick("dashboard_observe_inventory", "/dashboard/observe/inventory", "active", subCategoryValue),
                        selected: isCurrentSubCategory && (leftNavSelected === "dashboard_observe_inventory" + subCategoryIndex || leftNavSelected === "dashboard_observe_inventory"),
                    },
                    {
                        label: "Recent Changes",
                        onClick: () => handleClick("dashboard_observe_changes", "/dashboard/observe/changes", "active", subCategoryValue),
                        selected: isCurrentSubCategory && (leftNavSelected === "dashboard_observe_changes" + subCategoryIndex || leftNavSelected === "dashboard_observe_changes"),
                    },
                    {
                        label: "Sensitive Data",
                        onClick: () => handleClick("dashboard_observe_sensitive", "/dashboard/observe/sensitive", "active", subCategoryValue),
                        selected: isCurrentSubCategory && (leftNavSelected === "dashboard_observe_sensitive" + subCategoryIndex || leftNavSelected === "dashboard_observe_sensitive"),
                    },
                    ...(window?.STIGG_FEATURE_WISE_ALLOWED?.AKTO_DAST?.isGranted && dashboardCategory === CATEGORY_API_SECURITY ? [{
                        label: "DAST scans",
                        onClick: () => handleClick("dashboard_observe_dast_progress", "/dashboard/observe/dast-progress", "active", subCategoryValue),
                        selected: leftNavSelected === "dashboard_observe_dast_progress" + subCategoryIndex,
                    }] : []),
                    ...((dashboardCategory === "MCP Security" || dashboardCategory === "Agentic Security") ? [{
                        label: "Audit Data",
                        onClick: () => handleClick("dashboard_observe_audit", "/dashboard/observe/audit", "active", subCategoryValue),
                        selected: leftNavSelected === "dashboard_observe_audit" + subCategoryIndex,
                    }] : []),
                    ...((dashboardCategory === "MCP Security" || dashboardCategory === "Agentic Security") ? [{
                        label: "Endpoint Shield",
                        onClick: () => handleClick("dashboard_observe_endpoint_shield", "/dashboard/observe/endpoint-shield", "active", subCategoryValue),
                        selected: leftNavSelected === "dashboard_observe_endpoint_shield" + subCategoryIndex,
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
                            leftNavSelected.includes(subCategoryIndex) && leftNavSelected.includes("testing")
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
                onClick: () => handleClick("dashboard_testing", "/dashboard/testing/", "normal", subCategoryValue),
                selected: leftNavSelected.includes(subCategoryIndex) && leftNavSelected.includes("_testing"),
                subNavigationItems: [
                    {
                        label: "Results",
                        onClick: () => handleClick("dashboard_testing", "/dashboard/testing/", "active", subCategoryValue),
                        selected: leftNavSelected === "dashboard_testing" + subCategoryIndex,
                    },
                    {
                        label: mapLabel("Test", dashboardCategory) + " Roles",
                        onClick: () => handleClick("dashboard_testing_roles", "/dashboard/testing/roles", "active", subCategoryValue),
                        selected: leftNavSelected === "dashboard_testing_roles" + subCategoryIndex,
                    },
                    {
                        label: "User Config",
                        onClick: () => handleClick("dashboard_testing_user_config", "/dashboard/testing/user-config", "active", subCategoryValue),
                        selected: leftNavSelected === "dashboard_testing_user_config" + subCategoryIndex,
                    },
                    {
                        label:mapLabel("Test", dashboardCategory) + " Suite",
                        onClick: () => handleClick("dashboard_testing_test_suite", "/dashboard/testing/test-suite", "active", subCategoryValue),
                        selected: leftNavSelected === "dashboard_testing_test_suite" + subCategoryIndex,
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
                onClick: () => handleClick("dashboard_test_library_tests", "/dashboard/test-library/tests", "normal", subCategoryValue),
                selected: leftNavSelected.includes(subCategoryIndex) && leftNavSelected.includes("_test_library"),
                subNavigationItems: [
                    {
                        label: mapLabel("More Tests", dashboardCategory),
                        onClick: () => handleClick("dashboard_test_library_tests", "/dashboard/test-library/tests", "active", subCategoryValue),
                        selected: leftNavSelected === "dashboard_test_library_tests" + subCategoryIndex,
                    },
                    {
                        label: "Editor",
                        onClick: () => handleClick("dashboard_test_library_test_editor", "/dashboard/test-editor", "active", subCategoryValue),
                        selected: leftNavSelected === "dashboard_test_library_test_editor" + subCategoryIndex,
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
                onClick: () => handleClick("dashboard_prompt_hardening", "/dashboard/prompt-hardening", "normal", subCategoryValue),
                selected: leftNavSelected === "dashboard_prompt_hardening" + subCategoryIndex,
                key: "prompt_hardening",
            }] : []),
            {
                url: "#",
                label: (
                    <Text
                        variant="bodyMd"
                        fontWeight="medium"
                        color={
                            leftNavSelected.includes(subCategoryIndex) && leftNavSelected.includes("reports")
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
                onClick: () => handleClick("dashboard_reports_issues", "/dashboard/reports/issues", "normal", subCategoryValue),
                selected: leftNavSelected.includes(subCategoryIndex) && leftNavSelected.includes("_reports"),
                subNavigationItems: reportsSubNavigationItems,
                key: "6",
            },
            ...(window?.STIGG_FEATURE_WISE_ALLOWED?.THREAT_DETECTION?.isGranted && dashboardCategory === "API Security" ? [{
                    label: (
                        <Text variant="bodyMd" fontWeight="medium">
                            {mapLabel("Threat Detection", dashboardCategory)}
                        </Text>
                    ),
                    icon: DiamondAlertMinor,
                    onClick: () => handleClick("dashboard_threat_actor", "/dashboard/protection/threat-actor", "normal", subCategoryValue),
                    selected: leftNavSelected.includes(subCategoryIndex) && leftNavSelected.includes("_threat"),
                    url: "#",
                    key: "7",
                    subNavigationItems: [
                        ...(dashboardCategory === "API Security" && func.isDemoAccount() ? [{
                            label: "Dashboard",
                            onClick: () => handleClick("dashboard_threat_dashboard", "/dashboard/protection/threat-dashboard", "active", subCategoryValue),
                            selected: leftNavSelected === "dashboard_threat_dashboard" + subCategoryIndex,
                        }] : []),
                        {
                            label: "Threat Actors",
                            onClick: () => handleClick("dashboard_threat_actor", "/dashboard/protection/threat-actor", "active", subCategoryValue),
                            selected: leftNavSelected === "dashboard_threat_actor" + subCategoryIndex,
                        },
                        {
                            label: "Threat Activity",
                            onClick: () => handleClick("dashboard_threat_activity", "/dashboard/protection/threat-activity", "active", subCategoryValue),
                            selected:
                                leftNavSelected === "dashboard_threat_activity" + subCategoryIndex,
                        },
                        {
                            label: `${mapLabel("APIs", dashboardCategory)} Under Threat`,
                            onClick: () => handleClick("dashboard_threat_api", "/dashboard/protection/threat-api", "active", subCategoryValue),
                            selected:
                                leftNavSelected === "dashboard_threat_api" + subCategoryIndex,
                        },
                        {
                            label: "Threat Policy",
                            onClick: () => handleClick("dashboard_threat_policy", "/dashboard/protection/threat-policy", "active", subCategoryValue),
                            selected:
                                leftNavSelected === "dashboard_threat_policy" + subCategoryIndex,
                        },
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
            //         setActive("normal", subCategoryValue);
            //     },
            //     selected: leftNavSelected.includes(subCategoryIndex) && leftNavSelected.includes("agent_team"),
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
                onClick: () => handleClick("dashboard_mcp_guardrails", "/dashboard/guardrails/activity", "normal", subCategoryValue),
                selected: leftNavSelected.includes(subCategoryIndex) && (leftNavSelected.includes("_guardrails") || leftNavSelected.includes("_threat")),
                url: "#",
                key: "9",
                subNavigationItems: [
                    {
                        label: "Guardrail Activity",
                        onClick: () => handleClick("dashboard_threat_activity", "/dashboard/protection/threat-activity", "active", subCategoryValue),
                        selected:
                            leftNavSelected === "dashboard_threat_activity" + subCategoryIndex,
                    },
                    {
                        label: "Guardrails Policies",
                        onClick: () => handleClick("dashboard_guardrails_policies", "/dashboard/guardrails/policies", "active", subCategoryValue),
                        selected:
                            leftNavSelected === "dashboard_guardrails_policies" + subCategoryIndex,
                    },
                    {
                        label: "Guardrail Actors",
                        onClick: () => handleClick("dashboard_threat_actor", "/dashboard/protection/threat-actor", "active", subCategoryValue),
                        selected: leftNavSelected === "dashboard_threat_actor" + subCategoryIndex,
                    },
                    {
                        label: `${mapLabel("APIs", dashboardCategory)} Under Guardrail`,
                        onClick: () => handleClick("dashboard_threat_api", "/dashboard/protection/threat-api", "active", subCategoryValue),
                        selected: leftNavSelected === "dashboard_threat_api" + subCategoryIndex,
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
                onClick: () => handleClick("dashboard_ai_agent_guardrails", "/dashboard/guardrails/activity", "normal", subCategoryValue),
                selected: leftNavSelected.includes(subCategoryIndex) && leftNavSelected.includes("_guardrails"),
                url: "#",
                key: "10",
                subNavigationItems: [
                    {
                        label: "Guardrails Activity",
                        onClick: () => handleClick("dashboard_guardrails_activity", "/dashboard/guardrails/activity", "active", subCategoryValue),
                        selected: leftNavSelected === "dashboard_guardrails_activity" + subCategoryIndex,
                    },
                    {
                        label: "Guardrails Policies",
                        onClick: () => handleClick("dashboard_guardrails_policies", "/dashboard/guardrails/policies", "active", subCategoryValue),
                        selected:
                            leftNavSelected === "dashboard_guardrails_policies" + subCategoryIndex,
                    }
                ]
            }] : []),
            ...(dashboardCategory === "Agentic Security" ? [{
                label: (
                    <Text variant="bodyMd" fontWeight="medium">
                        Agentic Guardrails
                    </Text>
                ),
                icon: LockMajor,
                onClick: () => handleClick("dashboard_agentic_guardrails", "/dashboard/guardrails/activity", "normal", subCategoryValue),
                selected: leftNavSelected.includes(subCategoryIndex) && (leftNavSelected.includes("_guardrails") || leftNavSelected.includes("_threat")),
                url: "#",
                key: "11",
                subNavigationItems: [
                    {
                        label: "Guardrail Activity",
                        onClick: () => handleClick("dashboard_threat_activity", "/dashboard/protection/threat-activity", "active", subCategoryValue),
                        selected:
                            leftNavSelected === "dashboard_threat_activity" + subCategoryIndex,
                    },
                    {
                        label: "Guardrails Policies",
                        onClick: () => handleClick("dashboard_guardrails_policies", "/dashboard/guardrails/policies", "active", subCategoryValue),
                        selected:
                            leftNavSelected === "dashboard_guardrails_policies" + subCategoryIndex,
                    },
                    {
                        label: "Guardrail Actors",
                        onClick: () => handleClick("dashboard_threat_actor", "/dashboard/protection/threat-actor", "active", subCategoryValue),
                        selected: leftNavSelected === "dashboard_threat_actor" + subCategoryIndex,
                    },
                    {
                        label: `${mapLabel("APIs", dashboardCategory)} Under Guardrail`,
                        onClick: () => handleClick("dashboard_threat_api", "/dashboard/protection/threat-api", "active", subCategoryValue),
                        selected: leftNavSelected === "dashboard_threat_api" + subCategoryIndex,
                    }

                ]
            }] : [])
        ];
    }

    const navItems = useMemo(() => {

        const accountSection = [
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
            }
        ]

        const mainNavItems = getMainNavItems("Cloud Security");
        const shouldShowDivisions = dashboardCategory === "MCP Security" || dashboardCategory === "Agentic Security";
        
        let allItems;
        if (shouldShowDivisions) {
            const apiSecurityItems = getMainNavItems("Endpoint Security");
            allItems = [
                ...accountSection,
                {
                    label: (
                        <Box paddingBlockStart="4" paddingBlockEnd="2">
                            <div style={{ color: '#000000', fontWeight: 'bold', fontSize: '16px' }}>
                                Cloud Security
                            </div>
                        </Box>
                    ),
                    key: "cloud_header",
                    disabled: true,
                },
                ...mainNavItems,
                {
                    label: (
                        <Box paddingBlockStart="4" paddingBlockEnd="2">
                            <div style={{ color: '#000000', fontWeight: 'bold', fontSize: '16px' }}>
                                Endpoint Security
                            </div>
                        </Box>
                    ),
                    key: "endpoint_header", 
                    disabled: true,
                },
                ...apiSecurityItems
            ];
        }else{
            allItems = [
                ...accountSection,
                ...mainNavItems
            ];
        }

        return allItems
    }, [dashboardCategory, leftNavSelected, subcategory])

    const navigationMarkup = (
        <div className={active}>
            <Navigation location="/">
                <Navigation.Section
                    items={navItems}
                />
            </Navigation>
        </div>
    );

    return navigationMarkup;
}