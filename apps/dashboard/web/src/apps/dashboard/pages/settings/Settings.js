import { Button, Frame, Modal, Navigation, TextContainer } from "@shopify/polaris"
import { HomeMinor, OrdersMinor, ProductsMinor } from '@shopify/polaris-icons';

import { tokens } from "@shopify/polaris-tokens"
import { useCallback, useState } from "react";
import { Outlet, useNavigate } from "react-router-dom"
import './settings.css'

const SettingsNavbar = () => {
    return (
        <Navigation location="/">
            <Navigation.Section
                items={[
                    {
                        label: 'About',
                        icon: HomeMinor,
                        onClick: () => navigate("/settings/about")
                    },
                    {
                        label: 'Users',
                        icon: HomeMinor,
                        selected: true,
                        onClick: () => navigate("/settings/users")
                    },
                    {
                        label: 'Alerts',
                        icon: HomeMinor,
                        onClick: () => navigate("/settings/alerts")
                    },
                    {
                        label: 'CI/CD',
                        icon: HomeMinor,
                        onClick: () => navigate("/settings/cicd")
                    },
                    {
                        label: 'Health & Logs',
                        icon: HomeMinor,
                        onClick: () => navigate("/settings/health-logs")
                    },
                    {
                        label: 'Metrics',
                        icon: HomeMinor,
                        onClick: () => navigate("/settings/metrics")
                    },
                ]}
            />
        </Navigation>
    )
}

const Settings = () => {
    const navigate = useNavigate();

    return (
        <Modal
            fullScreen
            open
            onClose={() => navigate("/")}
            title="Settings"
        >
            <div style={{ background: tokens.color["color-bg-subdued"], display: "grid", gridTemplateColumns: "max-content auto" }}>
                <SettingsNavbar />
                <div style={{height: "100vh"}}>
                    <Outlet />
                </div>
            </div>   
        </Modal>
    )
}

export default Settings