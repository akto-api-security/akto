import {Box, Navigation, Text} from "@shopify/polaris";
import {
    AppsFilledMajor,
    InventoryFilledMajor,
    MarketingFilledMinor,
    ReportFilledMinor,
    DiamondAlertMinor,
    StarFilledMinor,
    FinancesMinor,
    LockFilledMajor,
} from "@shopify/polaris-icons";
import {useLocation, useNavigate} from "react-router-dom";

import "./LeftNav.css";
import PersistStore from "../../../../main/PersistStore";
import LocalStore from "../../../../main/LocalStorageStore";
import Store from "../../../store";
import api from "../../../../signup/api";
import {useEffect, useState} from "react";
import func from "@/util/func";
import Dropdown from "../Dropdown";
import SessionStore from "../../../../main/SessionStore";
import IssuesStore from "../../../pages/issues/issuesStore";
import { getNavConfig } from "./dashboardCategories";

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

    const [dashboardCategoryOptions, setDashboardCategoryOptions] = useState([]);

    useEffect(() => {
        setDashboardCategoryOptions(getNavConfig({ handleSelect, active, setActive, navigate, leftNavSelected })[dashboardCategory])
    }, [dashboardCategory, leftNavSelected])

    useEffect(() => {
        handleSelect(`dashboard_quick_start`);
        setActive("normal");
        navigate("/dashboard/quick-start");
    }, [dashboardCategory])

    const navigationMarkup = (
        <div className={active}>
            <Navigation location="/">
                <Navigation.Section
                    items={[
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
                        ...(dashboardCategoryOptions && dashboardCategoryOptions.length > 0 ? dashboardCategoryOptions : [])
                    ]}
                />
            </Navigation>
        </div>
    );

    return navigationMarkup;
}