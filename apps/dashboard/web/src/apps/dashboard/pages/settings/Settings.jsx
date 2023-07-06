import { Button, Card, Frame, HorizontalStack, Icon, Modal, Navigation, Scrollable, Text, TextContainer } from "@shopify/polaris"
import { HomeMinor, OrdersMinor, ProductsMinor, CancelMajor, SettingsMinor } from '@shopify/polaris-icons';
import { tokens } from "@shopify/polaris-tokens"
import { useCallback, useState } from "react";
import { Outlet, useNavigate } from "react-router-dom"
import './settings.css'
import SettingsLeftNav from "./nav/SettingsLeftNav";

const Settings = () => {
    const navigate = useNavigate();
    
    const toastConfig = Store(state => state.toastConfig)
    const setToastConfig = Store(state => state.setToastConfig)

    const disableToast = () => {
        setToastConfig({
            isActive: false,
            isError: false,
            message: ""
        })
    }

    const toastMarkup = toastConfig.isActive ? (
        <Toast content={toastConfig.message} error={toastConfig.isError} onDismiss={disableToast} duration={1500} />
    ) : null;

    return (
        <Frame>
            <Card>
                <div style={{ display: "grid", gridTemplateColumns: "4vw auto 1vw" }}>
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
                <SettingsLeftNav />
                <div style={{ minHeight: "100vh" }}>
                    <Outlet />
                </div>
            </div>
            {toastMarkup}
        </Frame>
    )
}
export default Settings