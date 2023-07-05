import { Button, Card, Frame, HorizontalStack, Icon, Modal, Navigation, Scrollable, Text, TextContainer } from "@shopify/polaris"
import { HomeMinor, OrdersMinor, ProductsMinor, CancelMajor, SettingsMinor } from '@shopify/polaris-icons';
import { tokens } from "@shopify/polaris-tokens"
import { useCallback, useState } from "react";
import { Outlet, useNavigate } from "react-router-dom"
import './settings.css'
import SettingsLeftNav from "./nav/SettingsLeftNav";

const Settings = () => {
    const navigate = useNavigate();
    return (
        <Frame>
            <Card>
                <div style={{ display: "grid", gridTemplateColumns: "4vw auto 1vw"}}>
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
                <div style={{ height: "100%" }}>
                    <Outlet />
                </div>
            </div>
        </Frame>
    )
}
export default Settings