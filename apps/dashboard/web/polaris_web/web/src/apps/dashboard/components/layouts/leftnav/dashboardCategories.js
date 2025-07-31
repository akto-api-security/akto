import {
    AffiliateMajor,
    AppsFilledMajor,
    CollectionsMajor,
    DiamondAlertMinor,
    FileFilledMinor,
    FinancesMinor,
    InventoryFilledMajor,
    MarketingFilledMinor,
    OrderStatusMinor,
    PointOfSaleMajor,
    ReportFilledMinor,
    StarFilledMinor,
} from "@shopify/polaris-icons";
import { Text } from "@shopify/polaris";
import Store from "../../../store";

export const getNavConfig = ({
        handleSelect,
        active,
        setActive,
        navigate,
        leftNavSelected
    }) => {

    const reportsSubNavigationItems = [
        {
            label: "Issues",
            onClick: () => {
                navigate("/dashboard/reports/issues");
                handleSelect("dashboard_reports_issues");
                setActive("active");
            },
            selected: leftNavSelected === "dashboard_reports_issues",
        },
    ];

    if (window?.USER_NAME?.includes("@akto.io")) {
        reportsSubNavigationItems.push({
        label: "Compliance",
        onClick: () => {
            navigate("/dashboard/reports/compliance");
            handleSelect("dashboard_reports_compliance");
            setActive("active");
        },
        selected: leftNavSelected === "dashboard_reports_compliance",
        });
    }

    return {
        "API Security": [
            {
                label: "API Security Posture",
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
                        API Discovery
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
                        label: "API Collections",
                        onClick: () => {
                            navigate("/dashboard/observe/inventory");
                            handleSelect("dashboard_observe_inventory");
                            setActive("active");
                        },
                        selected: leftNavSelected === "dashboard_observe_inventory",
                    },
                    {
                        label: "API Changes",
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
                        Testing
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
                        label: "Test Roles",
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
                        label: "Test Suite",
                        onClick: () => {
                            navigate("/dashboard/testing/test-suite");
                            handleSelect("dashboard_testing_test_suite");
                            setActive("active");
                        },
                        selected: leftNavSelected === "dashboard_testing_test_suite",
                    },
                ],
                key: "4",
            },
            {
                url: "#",
                label: (
                    <Text variant="bodyMd" fontWeight="medium">
                        Test library
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
                        label: "Tests",
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
        ],
        "MCP Security": [
            {
                label: (
                    <Text variant="bodyMd" fontWeight="medium">
                        MCP Discovery
                    </Text>
                ),
                icon: AffiliateMajor,
                onClick: () => {
                    handleSelect("dashboard_mcp_discovery");
                    setActive("normal");
                    navigate("/dashboard/mcp-security");
                },
                selected: leftNavSelected === "dashboard_mcp_discovery",
                key: "2",
            },
            {
                label: (
                    <Text variant="bodyMd" fontWeight="medium">
                        Testing
                    </Text>
                ),
                icon: MarketingFilledMinor,
                onClick: () => {
                    handleSelect("dashboard_mcp_testing");
                    setActive("normal");
                    navigate("/dashboard/mcp-security");
                },
                selected: leftNavSelected === "dashboard_mcp_testing",
                key: "3",
            },
        ],
        "Gen AI": [
            {
                label: "API Collections",
                icon: InventoryFilledMajor,
                onClick: () => {
                    navigate("/dashboard/observe/inventory");
                    handleSelect("dashboard_observe_inventory");
                    setActive("active");
                },
                selected: leftNavSelected === "dashboard_observe_inventory",
                key: "2",
            },
            {
                label: "Sensitive Data",
                icon: CollectionsMajor,
                onClick: () => {
                    navigate("/dashboard/observe/sensitive");
                    handleSelect("dashboard_observe_sensitive");
                    setActive("active");
                },
                selected: leftNavSelected === "dashboard_observe_sensitive",
                key: "3",
            },
            {
                label: "AI Agents",
                icon: StarFilledMinor,
                onClick: () => {
                    handleSelect("agent_team_members");
                    navigate("/dashboard/agent-team/members");
                    setActive("normal");
                },
                selected: leftNavSelected.includes("agent_team"),
                url: "#",
                key: "4",
            }
        ],
        "Threat Detection": [
            {
                label: "Threat Actors",
                icon: DiamondAlertMinor,
                onClick: () => {
                    navigate("/dashboard/protection/threat-actor");
                    handleSelect("dashboard_threat_actor");
                    setActive("active");
                },
                selected: leftNavSelected === "dashboard_threat_actor",
                key: "2",
            },
            {
                label: "Threat Activity",
                icon: OrderStatusMinor,
                onClick: () => {
                    navigate("/dashboard/protection/threat-activity");
                    handleSelect("dashboard_threat_activity");
                    setActive("active");
                },
                selected:
                    leftNavSelected === "dashboard_threat_activity",
                key: "3",
            },
            {
                label: "APIs Under Threat",
                icon: PointOfSaleMajor,
                onClick: () => {
                    navigate("/dashboard/protection/threat-api");
                    handleSelect("dashboard_threat_api");
                    setActive("active");
                },
                selected:
                    leftNavSelected === "dashboard_threat_api",
                key: "4",
            },
            {
                label: "Threat Policy",
                icon: FileFilledMinor,
                onClick: () => {
                    navigate("/dashboard/protection/threat-policy");
                    handleSelect("dashboard_threat_policy");
                    setActive("active");
                },
                selected:
                    leftNavSelected === "dashboard_threat_policy",
                key: "5",
            },
        ]
    };
};
