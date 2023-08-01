import { Button, Card, Frame, Icon, Text, Box } from "@shopify/polaris"
import { CancelMajor, SettingsMinor } from '@shopify/polaris-icons';
import { Outlet, useNavigate } from "react-router-dom"
import './settings.css'
import SettingsLeftNav from "./nav/SettingsLeftNav";

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

    return (
        <Frame navigation={<SettingsLeftNav />} topBar={<SettingsHeader />}>
            <Box paddingBlockEnd={"20"}>
                <Outlet />
            </Box>
        </Frame>
    )
}
export default Settings