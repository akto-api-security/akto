import { Button, Frame, Icon, Text, Box, TopBar, InlineStack } from "@shopify/polaris"
import { XIcon, SettingsIcon } from "@shopify/polaris-icons";
import { Outlet, useNavigate } from "react-router-dom"
import './settings.css'
import SettingsLeftNav from "./nav/SettingsLeftNav";
import PersistStore from "../../../main/PersistStore";

function SettingsHeader() {
    const navigate = useNavigate();
    const setActive = PersistStore(state => state.setActive)
    
    const handleSettingsClose = () => {
        navigate('/dashboard/testing')
        setActive('active')
    }

    const buttonComp = (
        <div className="header-css">
            <InlineStack gap="200">
                <Box>
                    <Icon source={SettingsIcon}/>
                </Box>
                <Text tone="text-inverse" variant="headingMd" as="h4">Settings</Text>
            </InlineStack>
            <Button  icon={XIcon} onClick={handleSettingsClose} variant="plain" size="large" />
        </div>
    )

    return (
        <TopBar secondaryMenu={buttonComp} />
    )
}

const Settings = () => {

    return (
        <Frame navigation={<SettingsLeftNav />} topBar={<SettingsHeader />}>
            <Box background="bg" paddingBlockEnd={"2000"}>
                <Outlet />
            </Box>
        </Frame>
    )
}
export default Settings