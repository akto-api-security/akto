import { Button, Frame, Icon, Text, Box, TopBar, HorizontalStack } from "@shopify/polaris"
import { CancelMajor, SettingsMinor } from '@shopify/polaris-icons';
import { Outlet, useNavigate } from "react-router-dom"
import './settings.css'
import SettingsLeftNav from "./nav/SettingsLeftNav";
import PersistStore from "../../../main/PersistStore";

function SettingsHeader() {
    const navigate = useNavigate();
    const setLeftNavSelected = PersistStore(state => state.setLeftNavSelected)
    const setActive = PersistStore(state => state.setActive)
    
    const handleSettingsClose = () => {
        navigate('/dashboard/testing')
        setLeftNavSelected("testing-results")
        setActive('active')
    }

    const buttonComp = (
        <div className="header-css">
            <HorizontalStack gap="2">
                <Box>
                    <Icon source={SettingsMinor}/>
                </Box>
                <Text variant="headingMd" as="h4">Settings</Text>
            </HorizontalStack>
            <Button plain icon={CancelMajor} onClick={handleSettingsClose} />
        </div>
    )

    return (
        <TopBar secondaryMenu={buttonComp} />
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