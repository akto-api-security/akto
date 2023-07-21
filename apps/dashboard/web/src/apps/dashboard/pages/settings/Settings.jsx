import { Button, Card, Frame, Icon, Text, Toast } from "@shopify/polaris"
import { CancelMajor, SettingsMinor } from '@shopify/polaris-icons';
import { tokens } from "@shopify/polaris-tokens"
import { Outlet, useLocation, useNavigate } from "react-router-dom"
import './settings.css'
import SettingsLeftNav from "./nav/SettingsLeftNav";
import homeFunctions from "../home/module";
import Store from "../../store";
import { useEffect } from "react";

const Settings = () => {
    const navigate = useNavigate();
    const location = useLocation();

    const setAllCollections = Store(state => state.setAllCollections)
    const toastConfig = Store(state => state.toastConfig)
    const setToastConfig = Store(state => state.setToastConfig)

    
    const fetchAllCollections = async()=>{
        let apiCollections = await homeFunctions.getAllCollections()
        setAllCollections(apiCollections)
    }
    useEffect(() => {
        fetchAllCollections()
    }, [])
    
    const disableToast = () => {
        setToastConfig({
            isActive: false,
            isError: false,
            message: ""
        })
    }

    const handleSettingsClose = () => {
        navigate('/dashboard/testing')
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
                    <Button icon={CancelMajor} onClick={handleSettingsClose} plain/>
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