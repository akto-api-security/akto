import { Navigation } from "@shopify/polaris"
import { RichTextMinor, 
    ProductsMinor, 
    LockMinor, 
    ReportMinor, 
    AnalyticsLineMinor, 
    AppsMinor,
    CustomersMinor,
    DiamondAlertMinor,
    MarketingMinor } from "@shopify/polaris-icons"
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
                        icon: RichTextMinor,
                        selected: page === "about",
                        onClick: () => navigate("/dashboard/settings/about")
                    },
                    {
                        label: 'Users',
                        icon: CustomersMinor,
                        selected: page === "users",
                        onClick: () => navigate("/dashboard/settings/users")
                    },
                    {
                        label: 'Alerts',
                        icon: DiamondAlertMinor,
                        selected: page === "alerts",
                        onClick: () => navigate("/dashboard/settings")
                    },
                    {
                        label: 'CI/CD',
                        icon: MarketingMinor,
                        selected: page === "cicd",
                        onClick: () => navigate("/dashboard/settings")
                    },
                    {
                        label: 'Integrations',
                        icon: AppsMinor,
                        selected: page === "integrations",
                        onClick: () => navigate("/dashboard/settings/integrations")
                    },
                    {
                        label: 'Health & Logs',
                        icon: ReportMinor,
                        selected: page === "health-logs",
                        onClick: () => navigate("/dashboard/settings/health-logs")
                    },
                    {
                        label: 'Metrics',
                        icon: AnalyticsLineMinor,
                        selected: page === "metrics",
                        onClick: () => navigate("/dashboard/settings/metrics")
                    },
                    {
                        label: 'Auth types',
                        icon: LockMinor,
                        selected: page === "auth-types",
                        onClick: () => navigate("/dashboard/settings/auth-types")
                    },
                    {
                        label: 'Tags',
                        icon: ProductsMinor,
                        selected: page === "tags",
                        onClick: () => navigate("/dashboard/settings/tags")
                    },
                ]}
            />
        </Navigation>
    )
}

export default SettingsLeftNav