import { Button, Modal, Navigation, TextContainer } from "@shopify/polaris"
import { HomeMinor, OrdersMinor, ProductsMinor } from '@shopify/polaris-icons';

import { tokens } from "@shopify/polaris-tokens"
import { useCallback, useState } from "react";
import { Outlet, useNavigate } from "react-router-dom"
import './settings.css'

const Settings = () => {
    const navigate = useNavigate();
    const [active, setActive] = useState(true);

    const handleChange = useCallback(() => navigate("/"));

    const activator = <Button onClick={handleChange}>Open</Button>;

    return (
        <Modal
            fullScreen
            open
            onClose={handleChange}
            noScroll
            title="Settings"
        >
            <div style={{background: tokens.color["color-bg-subdued"], display: "grid", gridTemplateColumns: "1fr 1.33fr" }}>
                <div>
                    <Navigation location="/">
                        <Navigation.Section
                            items={[
                                {
                                    label: 'About',
                                    icon: HomeMinor,
                                    onClick: () => navigate("/settings/about")
                                },
                                {
                                    url: 'users',
                                    label: 'Users',
                                    icon: HomeMinor,
                                },
                                {
                                    url: 'alerts',
                                    label: 'Alerts',
                                    icon: HomeMinor,
                                },
                                {
                                    url: 'cicd',
                                    label: 'CI/CD',
                                    icon: HomeMinor,
                                },
                                {
                                    url: 'health-logs',
                                    label: 'Health & Logs',
                                    icon: HomeMinor,
                                },
                                {
                                    url: 'metrics',
                                    label: 'Metrics',
                                    icon: HomeMinor,
                                },
                            ]}
                        />
                    </Navigation>
                </div>
                <div>
                    Hello
                </div>
            </div>
        </Modal>
    )
}

export default Settings