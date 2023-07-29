import { Button, Card, Frame, Icon, Text, Toast, Box } from "@shopify/polaris"
import { CancelMajor, SettingsMinor } from '@shopify/polaris-icons';
import { Outlet, useNavigate } from "react-router-dom"
import './settings.css'
import SettingsLeftNav from "./nav/SettingsLeftNav";
import homeFunctions from "../home/module";
import Store from "../../store";
import { useEffect } from "react";

function SettingsHeader() {
    const navigate = useNavigate();
    const handleSettingsClose = () => {
        navigate('/dashboard/testing')
    }

    return (
        <Card>
            <div style={{ display: "grid", gridTemplateColumns: "4vw auto 1vw"}}>
                <Icon source={SettingsMinor} color="base" />
                <Text variant="headingMd">
                    Settings
                </Text>
                <Button icon={CancelMajor} onClick={handleSettingsClose} plain />
            </div>
        </Card>
    )
}

const Settings = () => {

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

    const toastMarkup = toastConfig.isActive ? (
        <Toast content={toastConfig.message} error={toastConfig.isError} onDismiss={disableToast} duration={1500} />
    ) : null;

    return (
        <Frame navigation={<SettingsLeftNav />} topBar={<SettingsHeader />}>
            <Box paddingBlockEnd={"20"}>
                <Outlet />
            </Box>
            {toastMarkup}
        </Frame>
    )
}
export default Settings