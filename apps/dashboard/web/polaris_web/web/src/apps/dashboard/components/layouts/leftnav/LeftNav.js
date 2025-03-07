import {Navigation, Text} from "@shopify/polaris";
import {
    SettingsFilledMinor,
    AppsFilledMajor,
    InventoryFilledMajor,
    MarketingFilledMinor,
    ReportFilledMinor,
    DiamondAlertMinor,
    FinancesMinor,
} from "@shopify/polaris-icons";
import {useLocation, useNavigate} from "react-router-dom";

import "./LeftNav.css";
import PersistStore from "../../../../main/PersistStore";
import {useState} from "react";
import func from "@/util/func";

export default function LeftNav() {
    const navigate = useNavigate();
    const location = useLocation();
    const currPathString = func.transformString(location.pathname);

    const [leftNavSelected, setLeftNavSelected] = useState(currPathString);

    const active = PersistStore((state) => state.active);
    const setActive = PersistStore((state) => state.setActive);

    const handleSelect = (selectedId) => {
        setLeftNavSelected(selectedId);
    };

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

    const navigationMarkup = (
        <div className={active}>
            <Navigation location="/">
                <Navigation.Section
                    items={[
                        {
                            label: (
                                <Text variant="bodyMd" fontWeight="medium">
                                    Quick Start
                                </Text>
                            ),
                            icon: AppsFilledMajor,
                            onClick: () => {
                                handleSelect("dashboard_quick_start");
                                setActive("normal");
                                navigate("/dashboard/quick-start");
                            },
                            selected: leftNavSelected === "dashboard_quick_start",
                            key: "1",
                        },
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
                        window?.STIGG_FEATURE_WISE_ALLOWED?.THREAT_DETECTION?.isGranted || true ?
                             {
                                label: (
                                    <Text variant="bodyMd" fontWeight="medium">
                                        API Protection
                                    </Text>
                                ),
                                icon: DiamondAlertMinor,
                                onClick: () => {
                                    handleSelect("dashboard_threat_actor");
                                    navigate("/dashboard/protection/threat-actor");
                                    setActive("normal");
                                },
                                selected: leftNavSelected.includes("_threat"),
                                url: "#",
                                key: "7",
                                subNavigationItems: [
                                    {
                                        label: "Threat Actors",
                                        onClick: () => {
                                            navigate("/dashboard/protection/threat-actor");
                                            handleSelect("dashboard_threat_actor");
                                            setActive("active");
                                        },
                                        selected: leftNavSelected === "dashboard_threat_actor",
                                    },
                                    {
                                        label: "Threat Activity",
                                        onClick: () => {
                                            navigate("/dashboard/protection/threat-activity");
                                            handleSelect("dashboard_threat_activity");
                                            setActive("active");
                                        },
                                        selected:
                                            leftNavSelected === "dashboard_threat_activity",
                                    },
                                    {
                                        label: "APIs Under Threat",
                                        onClick: () => {
                                            navigate("/dashboard/protection/threat-api");
                                            handleSelect("dashboard_threat_api");
                                            setActive("active");
                                        },
                                        selected:
                                            leftNavSelected === "dashboard_threat_api",
                                    },
                                    {
                                        label: "Threat Policy",
                                        onClick: () => {
                                            navigate("/dashboard/protection/threat-policy");
                                            handleSelect("dashboard_threat_policy");
                                            setActive("active");
                                        },
                                        selected:
                                            leftNavSelected === "dashboard_threat_policy",
                                    },
                                ],
                            }
                            : {},
                    ]}
                />
                <Navigation.Section
                    items={[
                        {
                            label: (
                                <Text variant="bodyMd" fontWeight="medium">
                                    Settings
                                </Text>
                            ),
                            icon: SettingsFilledMinor,
                            onClick: () => {
                                navigate("/dashboard/settings/about");
                                setActive("normal");
                            },
                            selected: currPathString === "settings",
                            key: "7",
                        },
                    ]}
                />
            </Navigation>
        </div>
    );

    return navigationMarkup;
}
