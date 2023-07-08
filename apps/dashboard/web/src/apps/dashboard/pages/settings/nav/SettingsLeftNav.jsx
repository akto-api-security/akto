import { Navigation } from "@shopify/polaris"
import { HomeMinor } from "@shopify/polaris-icons"
import { useEffect, useState } from "react"
import { useLocation, useNavigate } from "react-router-dom"

const SettingsLeftNav = () => {
    const navigate = useNavigate()
    
    const location = useLocation()
    const path = location.pathname
    const page = path.substring(path.lastIndexOf('/') + 1)
    
    return (
        <Navigation>
            <Navigation.Section
                items={[
                    {
                        label: 'About',
                        icon: HomeMinor,
                        selected: page === "about",
                        onClick: () => navigate("/dashboard/settings/about")
                    },
                    {
                        label: 'Users',
                        icon: HomeMinor,
                        selected: page === "users",
                        onClick: () => navigate("/dashboard/settings/users")
                    },
                    {
                        label: 'Alerts',
                        icon: HomeMinor,
                        selected: page === "alerts",
                        onClick: () => navigate("/dashboard/settings/alerts")
                    },
                    {
                        label: 'CI/CD',
                        icon: HomeMinor,
                        selected: page === "cicd",
                        onClick: () => navigate("/dashboard/settings/cicd")
                    },
                    {
                        label: 'Integrations',
                        icon: HomeMinor,
                        selected: page === "integrations",
                        onClick: () => navigate("/dashboard/settings/integrations")
                    },
                    {
                        label: 'Health & Logs',
                        icon: HomeMinor,
                        selected: page === "health-logs",
                        onClick: () => navigate("/dashboard/settings/health_logs")
                    },
                    {
                        label: 'Metrics',
                        icon: HomeMinor,
                        selected: page === "metrics",
                        onClick: () => navigate("/dashboard/settings/metrics")
                    },
                ]}
            />
        </Navigation>
    )
}

export default SettingsLeftNav