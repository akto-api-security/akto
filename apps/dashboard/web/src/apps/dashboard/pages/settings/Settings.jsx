import { Button, Card, Frame, HorizontalStack, Icon, Modal, Navigation, Scrollable, Text, TextContainer } from "@shopify/polaris"
import { HomeMinor, OrdersMinor, ProductsMinor, CancelMajor, SettingsMinor } from '@shopify/polaris-icons';
import { tokens } from "@shopify/polaris-tokens"
import { useCallback, useState } from "react";
import { Outlet, useNavigate } from "react-router-dom"
import './settings.css'
const SettingsNavbar = () => {
    const navigate = useNavigate();

    return (
        <Navigation location="/">
            <Navigation.Section
                items={[
                    {
                        label: 'About',
                        icon: HomeMinor,
                        onClick: () => navigate("/dashboard/settings/about")
                    },
                    {
                        label: 'Users',
                        icon: HomeMinor,
                        selected: true,
                        onClick: () => navigate("/dashboard/settings/users")
                    },
                    {
                        label: 'Alerts',
                        icon: HomeMinor,
                        onClick: () => navigate("/dashboard/settings/alerts")
                    },
                    {
                        label: 'CI/CD',
                        icon: HomeMinor,
                        onClick: () => navigate("/dashboard/settings/cicd")
                    },
                    {
                        label: 'Health & Logs',
                        icon: HomeMinor,
                        onClick: () => navigate("/dashboard/settings/health-logs")
                    },
                    {
                        label: 'Metrics',
                        icon: HomeMinor,
                        onClick: () => navigate("/dashboard/settings/metrics")
                    },
                ]}
            />
        </Navigation>
    )
}
const Settings = () => {
    const navigate = useNavigate();
    return (
        <div style={{ position: "fixed", left: 0, top: 0, width: "100%", zIndex: tokens.zIndex["z-index-6"]}}>
            <Card>
                <div style={{display: "grid", gridTemplateColumns: "4vw auto 1vw"}}>
                    <Icon source={SettingsMinor} color="base" />
                    <Text variant="headingLg">
                        Settings
                    </Text>
                    <div onClick={() => navigate("/dashboard")}>
                        <Icon source={CancelMajor} color="base" />
                    </div>
                </div>    
            </Card>

            <div style={{ background: tokens.color["color-bg-subdued"], display: "grid", gridTemplateColumns: "max-content auto" }}>
                <SettingsNavbar />
                    <div style={{ height: "100%" }}>
                        <Outlet />
                   </div>  
            </div>
        </div>
    )
}
export default Settings